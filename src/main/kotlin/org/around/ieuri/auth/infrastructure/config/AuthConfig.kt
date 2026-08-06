package org.around.ieuri.auth.infrastructure.config

import org.around.ieuri.auth.application.policy.ProfileImagePolicy
import org.around.ieuri.auth.application.policy.VerificationPolicy
import org.around.ieuri.auth.domain.strategy.NicknameGenerator
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
    RbacProperties::class,
    PasswordProperties::class,
    SnsProperties::class,
    ProfileImageProperties::class,
)
class AuthConfig {

    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun profileImagePolicy(properties: ProfileImageProperties): ProfileImagePolicy =
        ProfileImagePolicy(
            uploadUrlTtl = properties.uploadUrlTtl,
            maxUploadBytes = properties.maxUploadBytes,
            allowedContentTypes = properties.allowedContentTypes.map { it.lowercase() }.toSet(),
        )

    /** 가입 시 닉네임 자동 생성(SPEC-0011 R20). 난수원은 기본값을 쓰고 테스트가 주입한다. */
    @Bean
    fun nicknameGenerator(): NicknameGenerator = NicknameGenerator()

    @Bean
    fun verificationPolicy(properties: VerificationProperties): VerificationPolicy =
        VerificationPolicy(
            ttl = Duration.ofMinutes(properties.ttlMinutes),
            maxAttempts = properties.maxAttempts,
            resendCooldown = Duration.ofSeconds(properties.resendCooldownSeconds),
        )
}
