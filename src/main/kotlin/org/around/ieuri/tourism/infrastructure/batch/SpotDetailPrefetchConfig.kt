package org.around.ieuri.tourism.infrastructure.batch

import org.around.ieuri.tourism.application.policy.SpotDetailPrefetchPolicy
import org.around.ieuri.tourism.application.port.`in`.command.SpotDetailPrefetchUseCase
import org.around.ieuri.tourism.application.port.out.client.TourInfoPort
import org.around.ieuri.tourism.application.port.out.persistence.SpotDetailUpsertPort
import org.around.ieuri.tourism.application.port.out.query.SpotDetailPrefetchTargetQueryPort
import org.around.ieuri.tourism.application.service.SpotDetailPrefetchService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.ThreadPoolExecutor

/**
 * 상세 사전 수집의 빈 조립(SPEC-0037). **batch 프로파일에서만 로드된다** — API 서버는 이 잡도
 * executor도 갖지 않는다.
 */
@Configuration
@Profile(BATCH_PROFILE)
@EnableConfigurationProperties(SpotDetailPrefetchProperties::class)
class SpotDetailPrefetchConfig {

    @Bean
    fun spotDetailPrefetchPolicy(properties: SpotDetailPrefetchProperties): SpotDetailPrefetchPolicy =
        SpotDetailPrefetchPolicy(
            refreshAfter = properties.refreshAfter,
            retryCount = properties.retryCount,
            retryBaseDelay = properties.retryBaseDelay,
            concurrency = properties.concurrency,
        )

    @Bean
    fun spotDetailPrefetchService(
        targetQueryPort: SpotDetailPrefetchTargetQueryPort,
        tourInfoPort: TourInfoPort,
        upsertPort: SpotDetailUpsertPort,
        @Qualifier(PREFETCH_EXECUTOR) executor: AsyncTaskExecutor,
        policy: SpotDetailPrefetchPolicy,
    ): SpotDetailPrefetchUseCase =
        SpotDetailPrefetchService(targetQueryPort, tourInfoPort, upsertPort, executor, policy)

    /**
     * 상세 확보 전용 executor. **write-behind executor와 성격이 정반대다** — 그쪽은 큐가 차면
     * 작업을 버리지만(캐시 충전이라 버려도 된다), 여기서는 배치 스레드가 결과를 기다리므로
     * 버려서는 안 된다. 제출이 거부되면 제출 스레드가 직접 실행하게 두어(`CallerRuns`) 자연히
     * 역압이 걸린다.
     *
     * 워커 수가 곧 동시 호출 상한이다. 유스케이스가 한 번에 던지는 것도 그 수만큼이라 큐는
     * 사실상 비어 있다 — `CallerRunsPolicy`는 방어일 뿐 정상 경로에서 발화하지 않는다.
     */
    @Bean(PREFETCH_EXECUTOR)
    fun spotDetailPrefetchExecutor(properties: SpotDetailPrefetchProperties): AsyncTaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = properties.concurrency
            maxPoolSize = properties.concurrency
            queueCapacity = properties.concurrency
            setThreadNamePrefix("spot-detail-prefetch-")
            // 거부되면 제출 스레드가 직접 실행한다 — 버리면 그 장소가 조용히 빠진다.
            setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
            setWaitForTasksToCompleteOnShutdown(true)
            setAwaitTerminationSeconds(SHUTDOWN_TIMEOUT_SECONDS)
            initialize()
        }

    companion object {
        const val PREFETCH_EXECUTOR = "spotDetailPrefetchExecutor"
        private const val SHUTDOWN_TIMEOUT_SECONDS = 30
    }
}
