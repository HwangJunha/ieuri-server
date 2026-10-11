package org.around.ieuri.auth.adapter.config
import org.around.ieuri.auth.application.member.ProfileImagePolicy
import org.around.ieuri.auth.domain.member.NicknameGenerator
import org.around.ieuri.auth.domain.member.VerificationLimits
import org.around.ieuri.auth.domain.token.spec.LiveSubscriptionIdentity
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

/** auth 도메인 인프라 빈 구성. 설정값을 애플리케이션 정책 타입으로 변환해 주입한다. */
@Configuration
@EnableConfigurationProperties(
    EncryptionProperties::class,
    VerificationProperties::class,
    JwtProperties::class,
    PasswordProperties::class,
    SnsProperties::class,
    ProfileImageProperties::class,
    SnsRevokeProperties::class,
    AuditRetentionProperties::class,
)
class AuthConfig {

    /** provider 토큰 교환·연결 해제용 RestClient. 탈퇴 트랜잭션 안에서 호출되므로 타임아웃이 곧 대기 상한이다(R11). */
    @Bean
    fun snsRevokeRestClient(builder: RestClient.Builder, properties: SnsRevokeProperties): RestClient {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(properties.timeout)
            setReadTimeout(properties.timeout)
        }
        return builder.requestFactory(requestFactory).build()
    }

    @Bean
    fun profileImagePolicy(properties: ProfileImageProperties): ProfileImagePolicy = ProfileImagePolicy(
        uploadUrlTtl = properties.uploadUrlTtl,
        maxUploadBytes = properties.maxUploadBytes,
        allowedContentTypes = properties.allowedContentTypes.map { it.lowercase() }.toSet(),
    )

    /** 가입 시 닉네임 자동 생성(SPEC-0011 R20). 난수원은 기본값을 쓰고 테스트가 주입한다. */
    @Bean
    fun nicknameGenerator(): NicknameGenerator = NicknameGenerator()

    @Bean
    fun verificationLimits(properties: VerificationProperties): VerificationLimits = VerificationLimits(
        ttl = Duration.ofMinutes(properties.ttlMinutes),
        maxAttempts = properties.maxAttempts,
        resendCooldown = Duration.ofSeconds(properties.resendCooldownSeconds),
    )

    /** 구독 신원의 수명은 액세스 토큰 TTL과 같다(SPEC-0032 R7). */
    @Bean
    fun liveSubscriptionIdentity(properties: JwtProperties): LiveSubscriptionIdentity = LiveSubscriptionIdentity(properties.accessTtl)
}
