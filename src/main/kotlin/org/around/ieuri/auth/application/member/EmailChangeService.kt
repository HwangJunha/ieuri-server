package org.around.ieuri.auth.application.member

import org.around.ieuri.auth.application.member.provided.EmailChangeUseCase
import org.around.ieuri.auth.application.member.provided.dto.ChangeEmailCommand
import org.around.ieuri.auth.application.member.provided.dto.ConfirmEmailChangeCommand
import org.around.ieuri.auth.application.member.provided.dto.ConfirmEmailChangeResult
import org.around.ieuri.auth.application.member.provided.dto.EmailChangeRequested
import org.around.ieuri.auth.application.member.provided.dto.ResendEmailChangeCommand
import org.around.ieuri.auth.application.member.provided.exception.InvalidEmailFormatException
import org.around.ieuri.auth.application.member.provided.exception.MemberNotFoundException
import org.around.ieuri.auth.application.member.provided.exception.VerificationNotFoundException
import org.around.ieuri.auth.application.member.required.LoginIdentifierProtectorPort
import org.around.ieuri.auth.application.member.required.MemberEventPublisherPort
import org.around.ieuri.auth.application.member.required.MemberRepository
import org.around.ieuri.auth.application.member.required.VerificationCodeGeneratorPort
import org.around.ieuri.auth.domain.member.Email
import org.around.ieuri.auth.domain.member.EmailVerification
import org.around.ieuri.auth.domain.member.EmailVerificationIssue
import org.around.ieuri.auth.domain.member.Member
import org.around.ieuri.auth.domain.member.Provider
import org.around.ieuri.auth.domain.member.ProviderUserId
import org.around.ieuri.auth.domain.member.VerificationCode
import org.around.ieuri.auth.domain.member.VerificationPolicy
import org.around.ieuri.auth.domain.member.VerificationPurpose
import org.around.ieuri.auth.domain.member.VerificationResult
import org.around.ieuri.auth.domain.member.event.EmailChanged
import org.around.ieuri.common.event.AccessType
import org.around.ieuri.common.event.MemberAccessed
import org.around.ieuri.common.persistence.ExposedConfig
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/**
 * 이메일 변경(SPEC-0011 R7·R8). 코드는 새 주소로 보내고, 통과하면 이메일을 바꾼다.
 * - 대상 주소는 확인 전에도 개인정보라 암호문으로만 저장된다(POL-012).
 */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX)
class EmailChangeService(
    private val memberRepository: MemberRepository,
    private val codeGenerator: VerificationCodeGeneratorPort,
    private val identifierProtector: LoginIdentifierProtectorPort,
    private val emailUniqueness: ProviderEmailUniqueness,
    private val verificationPolicy: VerificationPolicy,
    private val eventPublisher: MemberEventPublisherPort,
    private val clock: Clock,
) : EmailChangeUseCase {

    /** 형식 → 변경 가능 → 동일 주소 → 중복 순으로 본다. 회원의 이메일은 확인을 통과해야 바뀐다. */
    override fun changeEmail(command: ChangeEmailCommand): EmailChangeRequested {
        val newEmail = runCatching { Email(command.newEmail) }
            .getOrElse { throw InvalidEmailFormatException("이메일 형식이 올바르지 않습니다.") }
        val member = loadMember(command.memberId)

        member.ensureCanChangeEmail(newEmail)
        emailUniqueness.ensureAvailable(member.provider, newEmail, excludingMemberId = command.memberId)

        val plainCode = codeGenerator.generate()
        // 같은 용도의 이전 코드만 폐기한다 — 가입 인증이 진행 중이면 그건 그대로 둔다.
        val issue = member.requestEmailChange(newEmail, existingChallenges(command.memberId), newCode(plainCode), verificationPolicy, clock.instant())
        // 접속기록(기준 제8조). 개인정보(이메일) 변경이라 남겨야 하는 수행업무다.
        eventPublisher.publish(MemberAccessed(command.memberId, AccessType.EMAIL_CHANGE, command.ipAddress))
        return send(issue, newEmail, plainCode)
    }

    /** 남의 핸들이면 없는 것과 같은 오류로 답한다. 성패와 무관하게 시도 횟수·소진을 남긴다. */
    override fun confirmEmailChange(command: ConfirmEmailChangeCommand): ConfirmEmailChangeResult {
        val verification = loadOwnedChallenge(command.verificationPublicId, command.memberId, forUpdate = true)
        val member = loadMember(command.memberId)

        val attempt = member.verifyEmail(verification, codeGenerator.hash(command.code), clock.instant())
        memberRepository.saveEmailVerification(attempt.verification)

        val result = attempt.result
        if (result is VerificationResult.Failure) {
            return ConfirmEmailChangeResult(changed = false, email = null, failureReason = result.reason.name)
        }

        val newEmail = requireNotNull(verification.targetEmail) { "이메일 변경 챌린지에 대상 주소가 없습니다." }
        // 확인 시점에 다시 본다 — 요청 후 확인 사이에 다른 회원이 그 주소로 가입했을 수 있다.
        emailUniqueness.ensureAvailable(member.provider, newEmail, excludingMemberId = command.memberId)
        memberRepository.saveEmail(member.replaceEmail(newEmail, ProviderUserId(identifierProtector.protect(newEmail.value))))

        eventPublisher.publish(EmailChanged(command.memberId, member.provider, loginIdentifierUpdated = member.provider == Provider.EMAIL))
        return ConfirmEmailChangeResult(changed = true, email = newEmail.value, failureReason = null)
    }

    /** 쿨다운은 이메일 변경 용도로만 센다. 대상 주소는 이전 코드에서 이어받는다. */
    override fun resendEmailChange(command: ResendEmailChangeCommand): EmailChangeRequested {
        val previous = loadOwnedChallenge(command.verificationPublicId, command.memberId)
        val member = loadMember(command.memberId)

        val plainCode = codeGenerator.generate()
        val issue = member.reissueEmailChange(previous, existingChallenges(command.memberId), newCode(plainCode), verificationPolicy, clock.instant())
        return send(issue, requireNotNull(issue.issued.targetEmail), plainCode)
    }

    private fun send(issue: EmailVerificationIssue, toEmail: Email, plainCode: String): EmailChangeRequested {
        issue.consumed.forEach(memberRepository::saveEmailVerification)
        memberRepository.insertEmailVerification(issue.issued)
        eventPublisher.publish(VerificationEmailRequested(toEmail.value, plainCode))
        return EmailChangeRequested(issue.issued.publicId, issue.issued.expiresAt)
    }

    private fun loadMember(memberId: Long): Member = memberRepository.findById(memberId)
        ?: throw MemberNotFoundException("회원을 찾을 수 없습니다.")

    private fun existingChallenges(memberId: Long): List<EmailVerification> = memberRepository.findEmailVerifications(memberId, VerificationPurpose.EMAIL_CHANGE)

    private fun newCode(plainCode: String) = VerificationCode(UUID.randomUUID(), codeGenerator.hash(plainCode))

    /** 핸들을 호출자 소유의 이메일 변경 코드로만 읽는다. 아니면 없는 것과 같이 답한다(핸들 존재 비노출). */
    private fun loadOwnedChallenge(publicId: UUID, memberId: Long, forUpdate: Boolean = false): EmailVerification = memberRepository.findEmailVerification(publicId, forUpdate)
        ?.takeIf { it.memberId == memberId && it.purpose == VerificationPurpose.EMAIL_CHANGE }
        ?: throw VerificationNotFoundException("유효하지 않은 검증 요청입니다.")
}
