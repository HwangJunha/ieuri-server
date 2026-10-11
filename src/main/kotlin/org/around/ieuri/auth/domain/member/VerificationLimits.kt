package org.around.ieuri.auth.domain.member

import java.time.Duration
import java.time.Instant

/** 인증 코드 규칙값(만료·시도 제한·재발송 쿨다운). 프로파일별 설정에서 주입된다. */
data class VerificationLimits(
    val ttl: Duration,
    val maxAttempts: Int,
    val resendCooldown: Duration,
) {
    /** 마지막 발급에서 쿨다운이 지났는지. 발급한 적이 없으면 참이고, 정확히 쿨다운이 된 순간도 참이다. */
    fun cooldownPassed(lastIssuedAt: Instant?, now: Instant): Boolean = lastIssuedAt == null || !lastIssuedAt.plus(resendCooldown).isAfter(now)
}
