package org.around.ieuri.auth.domain.member

import java.time.Instant
import java.util.UUID

/**
 * 비밀번호 재설정 코드 한 건. 회원의 자식이며 [Member]가 만들고 검증한다.
 * - 규칙은 이메일 인증과 같다(짧은 만료·시도 제한·단회용). 코드는 해시로만 다룬다.
 */
data class PasswordReset(
    val id: Long?,
    val publicId: UUID,
    val memberId: Long,
    val codeHash: String,
    val expiresAt: Instant,
    val attempts: Int,
    val maxAttempts: Int,
    val consumed: Boolean,
    /** 발급 시각. DB가 채우므로 저장 전에는 null이다. */
    val createdAt: Instant?,
) {
    val active: Boolean get() = !consumed

    internal fun verify(inputCodeHash: String, now: Instant): Pair<PasswordReset, VerificationResult> {
        if (consumed) return this to failure(VerificationFailureReason.CONSUMED)
        if (now.isAfter(expiresAt)) return this to failure(VerificationFailureReason.EXPIRED)
        if (attempts >= maxAttempts) return copy(consumed = true) to failure(VerificationFailureReason.MAX_ATTEMPTS_EXCEEDED)
        if (inputCodeHash != codeHash) {
            val tried = attempts + 1
            return copy(attempts = tried, consumed = tried >= maxAttempts) to failure(VerificationFailureReason.CODE_MISMATCH)
        }
        return copy(consumed = true) to VerificationResult.Success
    }

    internal fun consume(): PasswordReset = copy(consumed = true)

    private fun failure(reason: VerificationFailureReason) = VerificationResult.Failure(reason)

    internal companion object {
        fun issue(memberId: Long, code: VerificationCode, policy: VerificationPolicy, now: Instant): PasswordReset = PasswordReset(
            id = null,
            publicId = code.publicId,
            memberId = memberId,
            codeHash = code.codeHash,
            expiresAt = now.plus(policy.ttl),
            attempts = 0,
            maxAttempts = policy.maxAttempts,
            consumed = false,
            createdAt = null,
        )
    }
}
