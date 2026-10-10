package org.around.ieuri.auth.application.member

import org.around.ieuri.auth.application.member.provided.PasswordResetUseCase
import org.around.ieuri.auth.application.member.provided.dto.RequestPasswordResetCommand
import org.around.ieuri.auth.application.member.provided.dto.ResetPasswordCommand
import org.around.ieuri.auth.application.member.provided.dto.ResetPasswordResult
import org.around.ieuri.auth.application.member.provided.exception.MemberNotFoundException
import org.around.ieuri.auth.application.member.required.LoginIdentifierProtectorPort
import org.around.ieuri.auth.application.member.required.MemberEventPublisherPort
import org.around.ieuri.auth.application.member.required.MemberRepository
import org.around.ieuri.auth.application.member.required.MemberSessionPort
import org.around.ieuri.auth.application.member.required.PasswordEncoderPort
import org.around.ieuri.auth.application.member.required.VerificationCodeGeneratorPort
import org.around.ieuri.auth.domain.member.Email
import org.around.ieuri.auth.domain.member.Member
import org.around.ieuri.auth.domain.member.PasswordHash
import org.around.ieuri.auth.domain.member.Provider
import org.around.ieuri.auth.domain.member.ProviderUserId
import org.around.ieuri.auth.domain.member.VerificationCode
import org.around.ieuri.auth.domain.member.VerificationPolicy
import org.around.ieuri.auth.domain.member.VerificationResult
import org.around.ieuri.auth.domain.member.event.PasswordResetCompleted
import org.around.ieuri.common.persistence.ExposedConfig
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/** 비밀번호 재설정. 평문 코드는 이벤트로만 나가고 커밋 뒤에 발송된다. */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX)
class PasswordResetService(
    private val memberRepository: MemberRepository,
    private val sessions: MemberSessionPort,
    private val codeGenerator: VerificationCodeGeneratorPort,
    private val passwordEncoder: PasswordEncoderPort,
    private val identifierProtector: LoginIdentifierProtectorPort,
    private val eventPublisher: MemberEventPublisherPort,
    private val verificationPolicy: VerificationPolicy,
    private val clock: Clock,
) : PasswordResetUseCase {

    /** 가입되지 않은 주소는 MEMBER_NOT_FOUND, SNS 계정은 NOT_EMAIL_MEMBER로 거부한다. */
    override fun requestPasswordReset(command: RequestPasswordResetCommand) {
        val now = clock.instant()
        val email = Email(command.email).value
        val member = findEmailMember(email) ?: throw MemberNotFoundException("가입되지 않은 이메일입니다.")
        val memberId = requireNotNull(member.id)

        val plainCode = codeGenerator.generate()
        // 쿨다운이 없으면 주소를 아는 제3자가 특정인의 메일함에 코드를 무제한 보낼 수 있다.
        val issue = member.requestPasswordReset(
            existing = memberRepository.findPasswordResets(memberId),
            code = VerificationCode(UUID.randomUUID(), codeGenerator.hash(plainCode)),
            policy = verificationPolicy,
            now = now,
        )
        issue.consumed.forEach(memberRepository::savePasswordReset)
        memberRepository.insertPasswordReset(issue.issued)
        eventPublisher.publish(PasswordResetRequested(memberId, email, plainCode))
    }

    /**
     * 계정 없음·SNS 계정·코드 없음·코드 불일치가 모두 `CODE_MISMATCH`다. 사유가 갈리면 인증 없이 계정을 열거할 수 있다.
     * - 세션 폐기는 트랜잭션 안에 둔다. 코드 확인 뒤에만 할 수 있고, 실패하면 코드 소진까지 되돌아가야 다시 시도할 수 있다.
     */
    override fun resetPassword(command: ResetPasswordCommand): ResetPasswordResult {
        val now = clock.instant()
        val member = findEmailMember(Email(command.email).value)?.takeIf { it.hasPassword } ?: return codeMismatch()
        val memberId = requireNotNull(member.id)
        val reset = member.activePasswordReset(memberRepository.findPasswordResets(memberId, forUpdate = true)) ?: return codeMismatch()

        val attempt = member.verifyPasswordReset(reset, codeGenerator.hash(command.code), now)
        // 성패와 무관하게 시도 횟수·소진을 남긴다.
        memberRepository.savePasswordReset(attempt.reset)

        val result = attempt.result
        if (result is VerificationResult.Failure) return ResetPasswordResult(success = false, failureReason = result.reason.name)

        memberRepository.savePassword(member.changePassword(PasswordHash(passwordEncoder.encode(command.newPassword))))
        sessions.revokeAll(memberId)
        eventPublisher.publish(PasswordResetCompleted(memberId))
        return ResetPasswordResult(success = true, failureReason = null)
    }

    private fun findEmailMember(email: String): Member? = memberRepository.findByProviderUserId(Provider.EMAIL, ProviderUserId(identifierProtector.protect(email)))

    private fun codeMismatch() = ResetPasswordResult(success = false, failureReason = CODE_MISMATCH)

    private companion object {
        const val CODE_MISMATCH = "CODE_MISMATCH"
    }
}
