package org.around.ieuri.tourism.adapter.config

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.atomic.AtomicLong

/**
 * tourism 비동기 executor(도메인별 격리). 종료 시 진행 중인 작업을 유한 시간만 기다린다.
 * - write-behind: 유계 큐가 차면 작업을 폐기하고 건수를 센다(다음 조회가 다시 발행한다).
 * - 탈퇴 정리: 폐기하지 않는다. 큐가 차면 호출 스레드가 직접 지운다 — 개인정보 파기라 유실이 허용되지 않는다.
 */
@Configuration
@EnableAsync
class TourismAsyncConfig {

    /** 폐기 건수. 꾸준히 늘면 큐 상한이나 워커 수가 부족하다는 신호다. */
    private val discarded = AtomicLong()

    @Bean(WRITE_BEHIND_EXECUTOR)
    fun spotDetailWriteBehindExecutor(): Executor = ThreadPoolTaskExecutor().apply {
        // 늘리면 DB 커넥션 풀을 잠식한다.
        corePoolSize = WORKER_COUNT
        maxPoolSize = WORKER_COUNT
        queueCapacity = QUEUE_CAPACITY
        setThreadNamePrefix("spot-detail-writer-")

        // 거부 = 폐기. 거부 1건당 한 번, 제출 스레드에서 호출된다.
        setRejectedExecutionHandler { _, _ ->
            val count = discarded.incrementAndGet()
            // 로그는 표본만 남긴다. 정확한 총계는 카운터가 갖는다.
            if (count % DISCARD_LOG_INTERVAL == 1L) {
                log.warn("spot detail write-behind discarded (total={})", count)
            }
        }

        // 종료 시 진행 중인 작업을 유한 시간만 기다린다.
        setWaitForTasksToCompleteOnShutdown(true)
        setAwaitTerminationSeconds(SHUTDOWN_TIMEOUT_SECONDS)
        initialize()
    }

    @Bean(WITHDRAWAL_EXECUTOR)
    fun memberWithdrawalExecutor(): Executor = ThreadPoolTaskExecutor().apply {
        corePoolSize = WITHDRAWAL_WORKER_COUNT
        maxPoolSize = WITHDRAWAL_WORKER_COUNT
        queueCapacity = QUEUE_CAPACITY
        setThreadNamePrefix("tourism-withdrawal-")
        setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())

        setWaitForTasksToCompleteOnShutdown(true)
        setAwaitTerminationSeconds(SHUTDOWN_TIMEOUT_SECONDS)
        initialize()
    }

    /** 관측용. 폐기 총계를 외부에서 확인할 수 있게 노출한다. */
    fun discardedCount(): Long = discarded.get()

    companion object {
        const val WRITE_BEHIND_EXECUTOR = "spotDetailWriteBehindExecutor"
        const val WITHDRAWAL_EXECUTOR = "tourismWithdrawalExecutor"
        private const val WORKER_COUNT = 2
        private const val WITHDRAWAL_WORKER_COUNT = 1
        private const val QUEUE_CAPACITY = 500
        private const val SHUTDOWN_TIMEOUT_SECONDS = 10
        private const val DISCARD_LOG_INTERVAL = 100L
        private val log = LoggerFactory.getLogger(TourismAsyncConfig::class.java)
    }
}
