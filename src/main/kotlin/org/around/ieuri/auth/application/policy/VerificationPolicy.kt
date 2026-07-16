package org.around.ieuri.auth.application.policy

import java.time.Duration

/**
 * 이메일 검증 정책 값. 프로파일별 설정에서 주입된다.
 * 6자리 코드 보안을 위한 짧은 만료·시도 제한·재발송 쿨다운을 담는다.
 */
data class VerificationPolicy(
    val ttl: Duration,
    val maxAttempts: Int,
    val resendCooldown: Duration,
)
