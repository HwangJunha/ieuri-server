package org.around.ieuri.community.infrastructure.config

import org.around.ieuri.community.application.policy.CommunityPolicy
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI

/**
 * community 도메인 인프라 빈 구성. @ConfigurationPropertiesScan이 없으므로 여기서 명시적으로
 * @EnableConfigurationProperties 한다(프로젝트 관례).
 */
@Configuration
@EnableConfigurationProperties(
    R2Properties::class,
    CommunityProperties::class,
    MessageRetentionProperties::class,
)
class CommunityConfig {

    /**
     * 타이핑 이벤트 구독용 pub/sub 컨테이너(SPEC-0017 R14).
     *
     * Spring Boot는 이 컨테이너를 자동구성하지 않는다 — 발행(convertAndSend)만 쓰는 기존
     * 코드에는 필요 없었고, 구독은 이 스펙이 처음 도입한다.
     *
     * 컨테이너가 리스너별로 스레드를 쓰므로, 구독이 끝나면 반드시 리스너를 떼야 한다
     * (ValkeyTypingSubscription.cancel).
     */
    @Bean
    fun redisMessageListenerContainer(
        connectionFactory: RedisConnectionFactory,
    ): RedisMessageListenerContainer = RedisMessageListenerContainer().apply {
        setConnectionFactory(connectionFactory)
    }

    @Bean
    fun communityPolicy(
        r2: R2Properties,
        community: CommunityProperties,
    ): CommunityPolicy = CommunityPolicy(
        maxPageSize = community.maxPageSize,
        commentPreviewSize = community.commentPreviewSize,
        maxRadiusMeters = community.maxRadiusMeters,
        uploadUrlTtl = r2.uploadUrlTtl,
        maxUploadBytes = r2.maxUploadBytes,
        allowedContentTypes = r2.allowedContentTypes.map { it.trim().lowercase() }.toSet(),
    )

    /**
     * R2는 리전 개념이 없어 `auto`를 쓴다. SigV4 서명에는 리전 문자열이 필요하므로 값 자체는
     * 있어야 하며, R2가 이 값을 요구하는 규약이다.
     *
     * path-style을 켜는 이유는 virtual-host 방식이 버킷명을 하위 도메인으로 붙이는데
     * R2 엔드포인트가 그 형태를 쓰지 않기 때문이다.
     */
    @Bean
    fun r2S3Client(properties: R2Properties): S3Client =
        S3Client.builder()
            .endpointOverride(URI.create(properties.s3Endpoint))
            .region(Region.of("auto"))
            .credentialsProvider(credentials(properties))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build()

    @Bean
    fun r2S3Presigner(properties: R2Properties): S3Presigner =
        S3Presigner.builder()
            .endpointOverride(URI.create(properties.s3Endpoint))
            .region(Region.of("auto"))
            .credentialsProvider(credentials(properties))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build()

    private fun credentials(properties: R2Properties) = StaticCredentialsProvider.create(
        AwsBasicCredentials.create(properties.accessKey, properties.secretAccessKey),
    )
}
