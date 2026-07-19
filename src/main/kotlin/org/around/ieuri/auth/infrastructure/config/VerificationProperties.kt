package org.around.ieuri.auth.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 이메일 검증 정책 설정. 6자리 코드 보안(짧은 만료·시도 제한·재발송 쿨다운)을 값으로 관리한다.
 */
@ConfigurationProperties(prefix = "ieuri.verification")
data class VerificationProperties(
    val ttlMinutes: Long = 10,
    val maxAttempts: Int = 5,
    val resendCooldownSeconds: Long = 60,
)
