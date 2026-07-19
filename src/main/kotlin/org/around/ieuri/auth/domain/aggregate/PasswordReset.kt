package org.around.ieuri.auth.domain.aggregate

import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * 비밀번호 재설정 챌린지 애그리거트. 이메일 검증과 목적은 다르지만(성공 시 비밀번호 변경) 코드 챌린지의
 * 보안 규칙은 동일하다: ① 짧은 만료 ② 시도 횟수 제한 ③ 단회용(consumed).
 * 실패 사유/결과는 [VerificationResult]를 재사용한다(코드 챌린지 공통 개념).
 * 코드는 평문이 아닌 해시(codeHash)로만 다룬다.
 */
class PasswordReset private constructor(
    val id: Long?,
    val publicId: UUID,
    val memberId: Long,
    val codeHash: String,
    val expiresAt: Instant,
    attemptsInitial: Int,
    val maxAttempts: Int,
    consumedInitial: Boolean,
    val createdAt: Instant,
) {
    var attempts: Int = attemptsInitial
        private set
    var consumed: Boolean = consumedInitial
        private set

    fun verify(inputCodeHash: String, now: Instant): VerificationResult {
        if (consumed) {
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
        ): PasswordReset = PasswordReset(
            id = null,
            publicId = publicId,
            memberId = memberId,
            codeHash = codeHash,
            expiresAt = now.plus(ttl),
            attemptsInitial = 0,
            maxAttempts = maxAttempts,
            consumedInitial = false,
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
            createdAt: Instant,
        ): PasswordReset = PasswordReset(
            id = id,
            publicId = publicId,
            memberId = memberId,
            codeHash = codeHash,
            expiresAt = expiresAt,
            attemptsInitial = attempts,
            maxAttempts = maxAttempts,
            consumedInitial = consumed,
            createdAt = createdAt,
        )
    }
}
