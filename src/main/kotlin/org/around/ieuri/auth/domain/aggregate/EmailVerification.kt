package org.around.ieuri.auth.domain.aggregate

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
 *   ① 짧은 만료(expiresAt) ② 시도 횟수 제한(attempts/maxAttempts)
 *   ③ 단회용(consumed) — 성공 또는 시도 초과 시 폐기.
 *
 * 코드는 평문이 아닌 해시로 다룬다(codeHash). 시간/랜덤/해시는 application·infrastructure에서
 * 주입하여 도메인은 순수 규칙만 담는다. member_id는 직접 노출하지 않고 publicId(핸들)로 식별한다.
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
) {
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

        fun issue(
            publicId: UUID,
            memberId: Long,
            codeHash: String,
            ttl: Duration,
            now: Instant,
            maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
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
        )
    }
}
