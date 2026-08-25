package org.around.ieuri.auth.infrastructure.config

import org.around.ieuri.auth.application.policy.ProfileImagePolicy
import org.around.ieuri.auth.application.policy.VerificationPolicy
import org.around.ieuri.auth.domain.strategy.NicknameGenerator
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * auth 도메인 인프라 빈 구성. 설정값(VerificationProperties)을 애플리케이션 정책 타입(VerificationPolicy)으로
 * 변환해 핸들러에 주입한다.
 *
 * Clock 빈은 여기 없다 — 여러 도메인이 쓰므로 `common/time/ClockConfig`로 옮겼다.
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
    SnsRevokeProperties::class,
    AuditRetentionProperties::class,
)
class AuthConfig {

    /**
     * provider 토큰 교환·연결 해제 호출용 RestClient. 타임아웃이 곧 R11의 대기 상한이다 —
     * 이 호출은 탈퇴 트랜잭션 안에서 일어나므로 상한이 없으면 트랜잭션이 무한정 길어진다.
     */
    @Bean
    fun snsRevokeRestClient(builder: RestClient.Builder, properties: SnsRevokeProperties): RestClient {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(properties.timeout)
            setReadTimeout(properties.timeout)
        }
        return builder.requestFactory(requestFactory).build()
    }

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
