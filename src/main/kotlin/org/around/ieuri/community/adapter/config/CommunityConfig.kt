package org.around.ieuri.community.adapter.config

import org.around.ieuri.community.application.shared.CommunityPolicy
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import reactor.core.scheduler.Scheduler
import reactor.core.scheduler.Schedulers
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI
import java.util.concurrent.Executors

/** community 도메인 인프라 빈 구성. */
@Configuration
@EnableConfigurationProperties(
    R2Properties::class,
    CommunityProperties::class,
    MessageRetentionProperties::class,
)
class CommunityConfig {

    /** 대화 실시간 이벤트 구독용 리액티브 pub/sub 컨테이너(SPEC-0017). Spring Boot가 자동구성하지 않는다. */
    @Bean
    fun reactiveRedisMessageListenerContainer(
        connectionFactory: ReactiveRedisConnectionFactory,
    ): ReactiveRedisMessageListenerContainer = ReactiveRedisMessageListenerContainer(connectionFactory)

    /**
     * 구독 이벤트가 이벤트 루프 밖에서 DB를 읽도록 옮기는 가상 스레드 스케줄러(SPEC-0017 R14).
     * - `spring.threads.virtual.enabled`는 리액티브 반환 경로에 적용되지 않으므로 여기서 직접 만든다.
     */
    @Bean(destroyMethod = "dispose")
    fun conversationEventScheduler(): Scheduler = Schedulers.fromExecutorService(
        Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("conversation-event-", 0).factory(),
        ),
        "conversation-event",
    )

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

    /** R2 클라이언트. 리전은 R2 규약대로 `auto`, 주소는 path-style이다. */
    @Bean
    fun r2S3Client(properties: R2Properties): S3Client = S3Client.builder()
        .endpointOverride(URI.create(properties.s3Endpoint))
        .region(Region.of("auto"))
        .credentialsProvider(credentials(properties))
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
        .build()

    @Bean
    fun r2S3Presigner(properties: R2Properties): S3Presigner = S3Presigner.builder()
        .endpointOverride(URI.create(properties.s3Endpoint))
        .region(Region.of("auto"))
        .credentialsProvider(credentials(properties))
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
        .build()

    private fun credentials(properties: R2Properties) = StaticCredentialsProvider.create(
        AwsBasicCredentials.create(properties.accessKey, properties.secretAccessKey),
    )
}
