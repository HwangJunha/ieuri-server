package org.around.ieuri.auth.domain.member
import org.around.ieuri.auth.domain.member.VerificationPurpose
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** 이메일 검증 실패 사유. */
enum class VerificationFailureReason {
    EXPIRED,
    CONSUMED,
    MAX_ATTEMPTS_EXCEEDED,
    CODE_MISMATCH,
}

sealed interface VerificationResult {
    data object Success : VerificationResult
    data class Failure(val reason: VerificationFailureReason) : VerificationResult
}

/**
 * 이메일 검증 챌린지 애그리거트. 6자리 코드 무차별 대입에 대비한 규칙을 캡슐화한다:
 * 짧은 만료, 시도 횟수 제한, 단회용(성공 또는 시도 초과 시 폐기).
 * - 코드는 해시(codeHash)로만 다루고, member_id 대신 publicId(핸들)로 식별한다.
 */
class EmailVerification private constructor(
    val id: Long?,
    val publicId: UUID,
    val memberId: Long,
    val codeHash: String,
    val expiresAt: Instant,
    attemptsInitial: Int,
    val maxAttempts: Int,
    consumedInitial: Boolean,
    verifiedAtInitial: Instant?,
    val createdAt: Instant,
    /** 무엇을 확인하는 챌린지인가. 코드를 보낼 주소와 통과 후 처리가 갈린다(SPEC-0011 R7). */
    val purpose: VerificationPurpose,
    /** 확정할 대상 주소의 암호문(POL-012). 도메인은 복호화하지 않는다. 대상이 없으면 null. */
    val targetEmailEncrypted: String?,
) {
    init {
        // purpose와 대상의 짝 규칙은 DB 제약(V63)과 같다. 가입 챌린지도 주소 정정(SPEC-0024 R7) 시에는 대상을 가진다.
        require(purpose != VerificationPurpose.EMAIL_CHANGE || targetEmailEncrypted != null) {
            "이메일 변경 챌린지는 대상 주소가 있어야 합니다."
        }
    }

    var attempts: Int = attemptsInitial
        private set
    var consumed: Boolean = consumedInitial
        private set
    var verifiedAt: Instant? = verifiedAtInitial
        private set

    fun verify(inputCodeHash: String, now: Instant): VerificationResult {
        if (consumed || verifiedAt != null) {
            return VerificationResult.Failure(VerificationFailureReason.CONSUMED)
        }
        if (now.isAfter(expiresAt)) {
            return VerificationResult.Failure(VerificationFailureReason.EXPIRED)
        }
        if (attempts >= maxAttempts) {
            consumed = true
            return VerificationResult.Failure(VerificationFailureReason.MAX_ATTEMPTS_EXCEEDED)
        }
        if (inputCodeHash != codeHash) {
            attempts += 1
            if (attempts >= maxAttempts) consumed = true
            return VerificationResult.Failure(VerificationFailureReason.CODE_MISMATCH)
        }
        verifiedAt = now
        consumed = true
        return VerificationResult.Success
    }

    companion object {
        const val DEFAULT_MAX_ATTEMPTS: Int = 5

        /** 새 챌린지 발급. [purpose]를 주지 않으면 가입 검증이며, 이메일 변경은 [targetEmailEncrypted]가 필수다. */
        fun issue(
            publicId: UUID,
            memberId: Long,
            codeHash: String,
            ttl: Duration,
            now: Instant,
            maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
            purpose: VerificationPurpose = VerificationPurpose.REGISTRATION,
            targetEmailEncrypted: String? = null,
        ): EmailVerification = EmailVerification(
            id = null,
            publicId = publicId,
            memberId = memberId,
            codeHash = codeHash,
            expiresAt = now.plus(ttl),
            attemptsInitial = 0,
            maxAttempts = maxAttempts,
            consumedInitial = false,
            verifiedAtInitial = null,
            createdAt = now,
            purpose = purpose,
            targetEmailEncrypted = targetEmailEncrypted,
        )

        /** 영속 상태에서 도메인 모델 재구성(매퍼 전용). */
        fun reconstitute(
            id: Long?,
            publicId: UUID,
            memberId: Long,
            codeHash: String,
            expiresAt: Instant,
            attempts: Int,
            maxAttempts: Int,
            consumed: Boolean,
            verifiedAt: Instant?,
            createdAt: Instant,
            purpose: VerificationPurpose,
            targetEmailEncrypted: String?,
        ): EmailVerification = EmailVerification(
            id = id,
            publicId = publicId,
            memberId = memberId,
            codeHash = codeHash,
            expiresAt = expiresAt,
            attemptsInitial = attempts,
            maxAttempts = maxAttempts,
            consumedInitial = consumed,
            verifiedAtInitial = verifiedAt,
            createdAt = createdAt,
            purpose = purpose,
            targetEmailEncrypted = targetEmailEncrypted,
        )
    }
}
