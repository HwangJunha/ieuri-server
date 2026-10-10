package org.around.ieuri.auth.domain.member

import java.time.Instant
import java.util.UUID

/**
 * 이메일 인증 코드 한 건. 회원의 자식이며 [Member]가 만들고 검증한다.
 * - 6자리 코드 무차별 대입에 대비한다: 짧은 만료, 시도 횟수 제한, 단회용(성공하거나 시도를 넘기면 폐기).
 * - 코드는 해시로만 다루고, 밖에서는 [publicId](핸들)로 가리킨다.
 */
data class EmailVerification(
    val id: Long?,
    val publicId: UUID,
    val memberId: Long,
    val codeHash: String,
    val expiresAt: Instant,
    val attempts: Int,
    val maxAttempts: Int,
    val consumed: Boolean,
    val verifiedAt: Instant?,
    /** 발급 시각. DB가 채우므로 저장 전에는 null이다. */
    val createdAt: Instant?,
    val purpose: VerificationPurpose,
    /** 통과하면 회원의 이메일이 될 주소. 가입 인증은 주소를 정정했을 때만 있다(SPEC-0024 R7). */
    val targetEmail: Email?,
) {
    init {
        require(purpose != VerificationPurpose.EMAIL_CHANGE || targetEmail != null) {
            "이메일 변경 챌린지는 대상 주소가 있어야 합니다."
        }
    }

    /** 아직 쓸 수 있는 코드인지(소진도 통과도 안 됨). */
    val active: Boolean get() = !consumed && verifiedAt == null

    internal fun verify(inputCodeHash: String, now: Instant): Pair<EmailVerification, VerificationResult> {
        if (consumed || verifiedAt != null) return this to failure(VerificationFailureReason.CONSUMED)
        if (now.isAfter(expiresAt)) return this to failure(VerificationFailureReason.EXPIRED)
        if (attempts >= maxAttempts) return copy(consumed = true) to failure(VerificationFailureReason.MAX_ATTEMPTS_EXCEEDED)
        if (inputCodeHash != codeHash) {
            val tried = attempts + 1
            return copy(attempts = tried, consumed = tried >= maxAttempts) to failure(VerificationFailureReason.CODE_MISMATCH)
        }
        return copy(verifiedAt = now, consumed = true) to VerificationResult.Success
    }

    internal fun consume(): EmailVerification = copy(consumed = true)

    private fun failure(reason: VerificationFailureReason) = VerificationResult.Failure(reason)

    internal companion object {
        fun issue(
            memberId: Long,
            code: VerificationCode,
            policy: VerificationPolicy,
            now: Instant,
            purpose: VerificationPurpose,
            targetEmail: Email?,
        ): EmailVerification = EmailVerification(
            id = null,
            publicId = code.publicId,
            memberId = memberId,
            codeHash = code.codeHash,
            expiresAt = now.plus(policy.ttl),
            attempts = 0,
            maxAttempts = policy.maxAttempts,
            consumed = false,
            verifiedAt = null,
            createdAt = null,
            purpose = purpose,
            targetEmail = targetEmail,
        )
    }
}
