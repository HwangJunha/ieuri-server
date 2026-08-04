package org.around.ieuri.tourism.infrastructure.adapter.out.event

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicLong

/**
 * write-behind 리스너 전용 executor. 유계 큐를 쓰고, 큐가 차면 작업을 **폐기**하며 폐기 건수를
 * 카운터로 남긴다. write-behind는 캐시 충전이지 데이터 보존이 아니므로 버려도 정합성이 깨지지 않는다
 * — 다음 조회 때 다시 발행된다.
 *
 * 종료 시 진행 중인 작업을 유한 시간 안에서만 기다린다. 각 적재는 어댑터에서 단일 트랜잭션이라
 * 중간에 끊겨도 절반만 쓰인 상태가 남지 않는다.
 */
@Configuration
@EnableAsync
class TourismAsyncConfig {

    /** 폐기 건수. 꾸준히 늘면 큐 상한이나 워커 수가 부족하다는 신호다. */
    private val discarded = AtomicLong()

    @Bean(WRITE_BEHIND_EXECUTOR)
    fun spotDetailWriteBehindExecutor(): Executor = ThreadPoolTaskExecutor().apply {
        // 적재는 I/O 대기가 대부분이라 소수로 충분하다. 늘리면 DB 커넥션 풀을 잠식한다.
        corePoolSize = WORKER_COUNT
        maxPoolSize = WORKER_COUNT
        queueCapacity = QUEUE_CAPACITY
        setThreadNamePrefix("spot-detail-writer-")

        // 거부 = 폐기. 핸들러는 거부 1건당 정확히 한 번, 제출 스레드에서 호출되므로 경합이 없다.
        setRejectedExecutionHandler { _, _ ->
            val count = discarded.incrementAndGet()
            // 폐기가 잦으면 로그가 폭주하므로 표본만 남긴다. 정확한 총계는 카운터가 갖는다.
            if (count % DISCARD_LOG_INTERVAL == 1L) {
                log.warn("spot detail write-behind discarded (total={})", count)
            }
        }

        // 종료 시 진행 중인 작업을 유한 시간만 기다린다.
        setWaitForTasksToCompleteOnShutdown(true)
        setAwaitTerminationSeconds(SHUTDOWN_TIMEOUT_SECONDS)
        initialize()
    }

    /** 관측용. 폐기 총계를 외부에서 확인할 수 있게 노출한다. */
    fun discardedCount(): Long = discarded.get()

    companion object {
        const val WRITE_BEHIND_EXECUTOR = "spotDetailWriteBehindExecutor"
        private const val WORKER_COUNT = 2
        private const val QUEUE_CAPACITY = 500
        private const val SHUTDOWN_TIMEOUT_SECONDS = 10
        private const val DISCARD_LOG_INTERVAL = 100L
        private val log = LoggerFactory.getLogger(TourismAsyncConfig::class.java)
    }
}
