package org.around.ieuri.auth.application.service

import org.around.ieuri.auth.application.command.ChangeEmailCommand
import org.around.ieuri.auth.application.command.ConfirmEmailChangeCommand
import org.around.ieuri.auth.application.command.ResendEmailChangeCommand
import org.around.ieuri.auth.application.event.VerificationEmailRequested
import org.around.ieuri.auth.application.exception.InvalidEmailFormatException
import org.around.ieuri.auth.application.exception.MemberNotFoundException
import org.around.ieuri.auth.application.exception.NotEmailMemberException
import org.around.ieuri.auth.application.exception.ResendCooldownActiveException
import org.around.ieuri.auth.application.exception.SameAsCurrentEmailException
import org.around.ieuri.auth.application.exception.VerificationNotFoundException
import org.around.ieuri.auth.application.policy.VerificationPolicy
import org.around.ieuri.auth.application.port.`in`.command.ConfirmEmailChangeResult
import org.around.ieuri.auth.application.port.`in`.command.EmailChangeRequested
import org.around.ieuri.auth.application.port.`in`.command.EmailChangeUseCase
import org.around.ieuri.auth.application.port.out.event.DomainEventPublisherPort
import org.around.ieuri.auth.application.port.out.persistence.AuditLogPort
import org.around.ieuri.auth.application.port.out.persistence.EmailVerificationRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.MemberRepositoryPort
import org.around.ieuri.auth.application.port.out.security.EmailEncryptorPort
import org.around.ieuri.auth.application.port.out.security.LoginIdentifierProtectorPort
import org.around.ieuri.auth.application.port.out.security.VerificationCodeGeneratorPort
import org.around.ieuri.auth.domain.aggregate.EmailVerification
import org.around.ieuri.auth.domain.aggregate.VerificationResult
import org.around.ieuri.auth.domain.vo.Email
import org.around.ieuri.auth.domain.vo.Provider
import org.around.ieuri.auth.domain.vo.VerificationPurpose
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * 이메일 변경(SPEC-0011 R7·R8). 가입 검증의 `EmailVerification`·`VerificationPolicy`를 공유한다.
 * - 코드는 새 주소로 보내고, 통과하면 이메일을 교체한다. EMAIL 회원은 로그인 식별자도 함께 바꾼다(R8).
 * - 대상 주소는 검증 전에도 암호문으로만 저장한다(POL-012).
 */
@Service
class EmailChangeService(
    private val memberRepository: MemberRepositoryPort,
    private val verificationRepository: EmailVerificationRepositoryPort,
    private val codeGenerator: VerificationCodeGeneratorPort,
    private val emailEncryptor: EmailEncryptorPort,
    private val identifierProtector: LoginIdentifierProtectorPort,
    private val emailUniqueness: ProviderEmailUniqueness,
    private val verificationPolicy: VerificationPolicy,
    private val auditLog: AuditLogPort,
    private val eventPublisher: DomainEventPublisherPort,
    private val clock: java.time.Clock,
) : EmailChangeUseCase {

    /**
     * 변경 요청. 형식 → 동일 주소 → 중복 순으로 보고, 통과하면 새 주소로 코드를 보낸다.
     * - 회원의 이메일은 확인을 통과해야 바뀐다. 진행 중이던 이메일 변경 챌린지는 폐기한다.
     */
    @Transactional
    override fun changeEmail(command: ChangeEmailCommand): EmailChangeRequested {
        val newEmail = runCatching { Email(command.newEmail) }
            .getOrElse { throw InvalidEmailFormatException("이메일 형식이 올바르지 않습니다.") }

        val member = memberRepository.load(command.memberId)
            ?: throw MemberNotFoundException("회원을 찾을 수 없습니다.")
        // 변경 가능 여부는 도메인이 판정한다(SPEC-0024 R6). confirm·resend는 여기를 거쳐야 도달하므로 다시 보지 않는다.
        if (!member.canChangeEmail()) {
            throw NotEmailMemberException("SNS 계정은 이메일을 변경할 수 없습니다.")
        }
        if (member.email.value == newEmail.value) {
            throw SameAsCurrentEmailException("현재 사용 중인 이메일과 같습니다.")
        }
        emailUniqueness.ensureAvailable(member.provider, newEmail, excludingMemberId = command.memberId)

        val now = clock.instant()
        // 같은 용도의 이전 챌린지만 폐기한다 — 가입 검증이 진행 중이면 그건 그대로 둔다.
        verificationRepository.consumeActiveByMemberIdAndPurpose(
            command.memberId,
            VerificationPurpose.EMAIL_CHANGE,
            now,
        )
        return issueChallenge(command.memberId, newEmail, now)
    }

    /**
     * 코드 확인. 성패와 무관하게 attempts·consumed를 반영한다(무차별 대입 방지).
     * - 남의 핸들이면 존재하지 않는 것과 같은 오류로 응답한다.
     */
    @Transactional
    override fun confirmEmailChange(command: ConfirmEmailChangeCommand): ConfirmEmailChangeResult {
        val verification = loadOwnedChallenge(command.verificationPublicId, command.memberId)
        val now = clock.instant()

        val result = verification.verify(codeGenerator.hash(command.code), now)
        verificationRepository.update(verification)

        if (result is VerificationResult.Failure) {
            return ConfirmEmailChangeResult(changed = false, email = null, failureReason = result.reason.name)
        }

        val newEmail = Email(
            emailEncryptor.decrypt(
                requireNotNull(verification.targetEmailEncrypted) { "이메일 변경 챌린지에 대상 주소가 없습니다." },
            ),
        )
        val member = memberRepository.load(command.memberId)
            ?: throw MemberNotFoundException("회원을 찾을 수 없습니다.")
        // 확인 시점에 다시 본다 — 요청 후 확인 사이에 다른 회원이 그 주소로 가입했을 수 있다.
        emailUniqueness.ensureAvailable(member.provider, newEmail, excludingMemberId = command.memberId)

        // EMAIL 회원만 provider_user_id가 이메일에서 파생된다(R8). SNS는 provider가 준 값이라 두 값이 무관하다.
        val protectedIdentifier =
            if (member.provider == Provider.EMAIL) identifierProtector.protect(newEmail.value) else null

        memberRepository.updateEmail(
            memberId = command.memberId,
            newEmail = newEmail,
            emailIdentifier = emailUniqueness.identifierOf(newEmail),
            protectedIdentifier = protectedIdentifier,
        )

        // 주소 자체는 남기지 않는다(POL-013) — 무엇이 바뀌었는지는 회원 데이터가 갖는다.
        auditLog.record(
            memberId = command.memberId,
            eventType = "EMAIL_CHANGED",
            payload = mapOf(
                "provider" to member.provider.name,
                "loginIdentifierUpdated" to (protectedIdentifier != null),
            ),
        )
        return ConfirmEmailChangeResult(changed = true, email = newEmail.value, failureReason = null)
    }

    /** 재발송. 쿨다운은 이메일 변경 용도로만 센다. 대상 주소는 이전 챌린지에서 이어받는다. */
    @Transactional
    override fun resendEmailChange(command: ResendEmailChangeCommand): EmailChangeRequested {
        val existing = loadOwnedChallenge(command.verificationPublicId, command.memberId)
        val now = clock.instant()

        val lastCreatedAt = verificationRepository.findLatestCreatedAtByMemberIdAndPurpose(
            command.memberId,
            VerificationPurpose.EMAIL_CHANGE,
        )
        if (lastCreatedAt != null && lastCreatedAt.plus(verificationPolicy.resendCooldown).isAfter(now)) {
            throw ResendCooldownActiveException("잠시 후 다시 시도해 주세요.")
        }

        val targetEmail = Email(
            emailEncryptor.decrypt(
                requireNotNull(existing.targetEmailEncrypted) { "이메일 변경 챌린지에 대상 주소가 없습니다." },
            ),
        )
        verificationRepository.consumeActiveByMemberIdAndPurpose(
            command.memberId,
            VerificationPurpose.EMAIL_CHANGE,
            now,
        )
        return issueChallenge(command.memberId, targetEmail, now)
    }

    /** 새 챌린지를 만들고 새 주소로 코드를 보낸다. 평문 코드는 이벤트로만 전달되어 AFTER_COMMIT에 발송된다. */
    private fun issueChallenge(memberId: Long, targetEmail: Email, now: java.time.Instant): EmailChangeRequested {
        val plainCode = codeGenerator.generate()
        val verification = EmailVerification.issue(
            publicId = UUID.randomUUID(),
            memberId = memberId,
            codeHash = codeGenerator.hash(plainCode),
            ttl = verificationPolicy.ttl,
            now = now,
            maxAttempts = verificationPolicy.maxAttempts,
            purpose = VerificationPurpose.EMAIL_CHANGE,
            targetEmailEncrypted = emailEncryptor.encrypt(targetEmail.value),
        )
        verificationRepository.save(verification)

        eventPublisher.publish(VerificationEmailRequested(targetEmail.value, plainCode))
        return EmailChangeRequested(verification.publicId, verification.expiresAt)
    }

    /** 핸들을 호출자 소유의 이메일 변경 챌린지로만 해석한다. 아니면 없는 것과 같이 응답한다(핸들 존재 비노출). */
    private fun loadOwnedChallenge(publicId: UUID, memberId: Long): EmailVerification {
        val verification = verificationRepository.findByPublicId(publicId)
        if (verification == null ||
            verification.memberId != memberId ||
            verification.purpose != VerificationPurpose.EMAIL_CHANGE
        ) {
            throw VerificationNotFoundException("유효하지 않은 검증 요청입니다.")
        }
        return verification
    }
}
