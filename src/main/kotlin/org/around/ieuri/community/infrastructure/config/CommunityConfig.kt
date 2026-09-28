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
import java.util.concurrent.Executors

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
    ): ReactiveRedisMessageListenerContainer = ReactiveRedisMessageListenerContainer(connectionFactory)

    /**
     * 구독 이벤트가 DB를 읽을 때 쓰는 스케줄러(SPEC-0017 R14).
     *
     * 구독 스트림은 Lettuce의 이벤트 루프에서 방출되는데, 그 스레드 하나가 연결의 모든 채널을
     * 나르므로 거기서 블로킹 조회를 돌리면 무관한 대화까지 함께 멎는다. 그래서 스레드를 옮긴다.
     *
     * **가상 스레드라 개수를 정할 필요가 없다** — 블로킹 시 캐리어를 반납하므로 풀 크기가
     * 동시 처리량의 상한이 되지 않는다.
     *
     * `spring.threads.virtual.enabled`로는 이 지점이 켜지지 않는다. Spring·DGS 모두 반환 타입이
     * 리액티브면 그 경로에서 제외하는데, Subscription은 Publisher 반환이 강제라 전부 걸린다.
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

    /**
     * R2는 리전 개념이 없어 `auto`를 쓴다. SigV4 서명에는 리전 문자열이 필요하므로 값 자체는
     * 있어야 하며, R2가 이 값을 요구하는 규약이다.
     *
     * path-style을 켜는 이유는 virtual-host 방식이 버킷명을 하위 도메인으로 붙이는데
     * R2 엔드포인트가 그 형태를 쓰지 않기 때문이다.
     */
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
