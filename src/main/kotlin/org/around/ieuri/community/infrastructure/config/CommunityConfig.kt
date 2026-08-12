package org.around.ieuri.community.infrastructure.config

import org.around.ieuri.community.application.policy.CommunityPolicy
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
     * 대화 실시간 이벤트 구독용 pub/sub 컨테이너(SPEC-0017 R10·R14).
     *
     * Spring Boot는 이 컨테이너를 자동구성하지 않는다 — 발행(convertAndSend)만 쓰는 기존
     * 코드에는 필요 없었고, 구독은 이 스펙이 처음 도입한다.
     *
     * **명령형 컨테이너가 아니라 리액티브 쪽을 쓴다.** 명령형은 리스너를 직접 붙이고 떼야 해서
     * 채널당 싱크 캐시와 해제 경로를 손으로 만들게 되는데, 그 해제를 빠뜨리면 파드가 사는 동안
     * 브로커 구독이 단조 증가한다. `receive()`가 돌려주는 Flux는 **마지막 구독이 취소될 때
     * 컨테이너가 UNSUBSCRIBE까지 처리**하므로 그 책임이 통째로 사라진다.
     */
    @Bean
    fun reactiveRedisMessageListenerContainer(
        connectionFactory: ReactiveRedisConnectionFactory,
    ): ReactiveRedisMessageListenerContainer =
        ReactiveRedisMessageListenerContainer(connectionFactory)

    /**
     * 구독 이벤트가 DB를 읽을 때 쓰는 **전용** 스레드 풀(SPEC-0017 R14).
     *
     * **전역 `Schedulers.boundedElastic()`을 쓰지 않는다.** 그것은 JVM 싱글턴이라 나중에 다른
     * 도메인이 같은 풀을 쓰면 서로의 스레드를 잠식한다 — 한 기능의 과부하가 무관한 기능을
     * 멈추게 한다. 전용 풀로 격리하면 최악의 경우에도 피해가 이 기능 안에 갇힌다.
     *
     * **크기는 커넥션 풀보다 작아야 한다.** 스레드가 커넥션(Hikari 기본 10)보다 많으면 남는
     * 스레드는 커넥션을 기다리다 타임아웃 예외를 낸다 — 처리량은 그대로인데 실패만 는다.
     *
     * 큐를 짧게 잡는 이유는 과부하를 **빠르게 드러내기 위해서다.** 기본값(10만)은 사실상
     * 무한이라 이벤트가 몇 분씩 밀리다 메모리로 죽는데, 그 실패는 조용해서 추적이 어렵다.
     */
    @Bean(destroyMethod = "dispose")
    fun conversationEventScheduler(): Scheduler =
        Schedulers.newBoundedElastic(
            CONVERSATION_EVENT_THREADS,
            CONVERSATION_EVENT_QUEUE,
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

    private companion object {
        /**
         * Hikari 기본 풀(10)보다 작게 잡는다 — 스레드가 커넥션보다 많으면 초과분은 커넥션을
         * 기다리다 타임아웃으로 실패할 뿐, 처리량이 늘지 않는다.
         */
        const val CONVERSATION_EVENT_THREADS = 8

        /** 과부하를 빠르게 드러내기 위한 짧은 큐. 넘치면 즉시 거부되어 조용히 밀리지 않는다. */
        const val CONVERSATION_EVENT_QUEUE = 256
    }
}
