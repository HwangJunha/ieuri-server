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
 * 이메일 변경(SPEC-0011 R7·R8).
 *
 * 가입 검증과 **같은 챌린지 구조를 공유한다** — `EmailVerification` 애그리거트와
 * `VerificationPolicy`(만료 10분·시도 5회·쿨다운 60초)를 그대로 쓴다. 코드 발급·검증 규칙을
 * 두 번 구현하지 않기 위해서다. 갈리는 것은 두 가지뿐이다:
 *
 *  1. 코드를 **새 주소로** 보낸다. 아직 회원의 것이 아니므로 챌린지가 대상을 들고 있어야 한다.
 *  2. 통과하면 이메일을 교체한다. `Provider.EMAIL` 회원은 **로그인 식별자도 함께** 바꾼다(R8).
 *
 * 대상 주소는 검증 통과 전에도 개인정보라 암호문으로만 저장한다(POL-012).
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
     *
     * **회원의 이메일은 아직 바뀌지 않는다** — 확인을 통과해야 교체된다.
     * 진행 중이던 이메일 변경 챌린지는 폐기한다(주소를 바꿔 다시 요청하는 경우).
     */
    @Transactional
    override fun changeEmail(command: ChangeEmailCommand): EmailChangeRequested {
        val newEmail = runCatching { Email(command.newEmail) }
            .getOrElse { throw InvalidEmailFormatException("이메일 형식이 올바르지 않습니다.") }

        val member = memberRepository.load(command.memberId)
            ?: throw MemberNotFoundException("회원을 찾을 수 없습니다.")
        // 변경 가능 여부는 도메인에 묻는다(SPEC-0024 R6) — provider 판정은 불변식이지
        // 이 서비스의 분기가 아니다. `confirmEmailChange`·`resendEmailChange`에는 같은
        // 판정을 두지 않는다. 여기서 막히면 도달할 수 없는 경로이고, 세 곳에 복제하면
        // 어긋날 여지만 생긴다.
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
     *
     * **핸들이 호출자의 것인지 확인한다** — 남의 핸들로 자기 이메일을 바꾸거나 그 반대를
     * 막는다. 존재하지 않는 것과 같은 오류로 응답해 핸들의 존재 여부가 새지 않게 한다.
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

    /**
     * 재발송. 쿨다운은 **이메일 변경 용도로만** 센다 — 가입 검증과 창을 공유하면
     * 한쪽을 보낸 직후 다른 쪽이 막힌다.
     *
     * 이전 챌린지의 대상 주소를 그대로 이어받는다. 주소를 바꾸려면 changeEmail을 다시 부른다.
     */
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

    /**
     * 새 챌린지를 만들고 **새 주소로** 코드를 보낸다. 평문 코드는 저장하지 않고 이벤트로만
     * 전달되어 AFTER_COMMIT에 발송된다 — 트랜잭션이 롤백되면 메일도 나가지 않는다.
     */
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

    /**
     * 핸들을 호출자 소유의 **이메일 변경** 챌린지로만 해석한다. 남의 핸들이거나 가입 검증
     * 핸들이면 없는 것과 같이 응답한다 — 사유를 구분해 알려 주면 남의 핸들 존재가 새어 나간다.
     */
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
