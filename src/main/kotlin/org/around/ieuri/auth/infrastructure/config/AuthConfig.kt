package org.around.ieuri.auth.infrastructure.config

import org.around.ieuri.auth.application.policy.VerificationPolicy
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.Duration

/**
 * auth 도메인 인프라 빈 구성. 설정값(VerificationProperties)을 애플리케이션 정책 타입(VerificationPolicy)으로
 * 변환해 핸들러에 주입하고, 테스트 가능성을 위해 Clock을 빈으로 제공한다.
 */
@Configuration
@EnableConfigurationProperties(
    EncryptionProperties::class,
    VerificationProperties::class,
    JwtProperties::class,
    AdminProperties::class,
)
class AuthConfig {

    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun verificationPolicy(properties: VerificationProperties): VerificationPolicy =
        VerificationPolicy(
            ttl = Duration.ofMinutes(properties.ttlMinutes),
            maxAttempts = properties.maxAttempts,
            resendCooldown = Duration.ofSeconds(properties.resendCooldownSeconds),
        )
}
