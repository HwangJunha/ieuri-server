package org.around.ieuri.alarm.infrastructure.config

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicLong

/**
 * 푸시 발송 전용 executor. 도메인마다 executor를 따로 둬 격리한다.
 * - 큐가 차면 폐기한다(POL-048, AC12.3).
 */
@Configuration
@EnableAsync
class AlarmAsyncConfig {

    /** 폐기 건수. */
    private val discarded = AtomicLong()

    @Bean(PUSH_EXECUTOR)
    fun pushDispatchExecutor(): Executor = ThreadPoolTaskExecutor().apply {
        corePoolSize = WORKER_COUNT
        maxPoolSize = WORKER_COUNT
        queueCapacity = QUEUE_CAPACITY
        setThreadNamePrefix("push-dispatcher-")

        setRejectedExecutionHandler { _, _ ->
            val count = discarded.incrementAndGet()
            // 표본만 로그로 남긴다. 총계는 카운터가 갖는다.
            if (count % DISCARD_LOG_INTERVAL == 1L) {
                log.warn("push dispatch discarded (total={})", count)
            }
        }

        setWaitForTasksToCompleteOnShutdown(true)
        setAwaitTerminationSeconds(SHUTDOWN_TIMEOUT_SECONDS)
        initialize()
    }

    /** 폐기 총계(관측용). */
    fun discardedCount(): Long = discarded.get()

    companion object {
        const val PUSH_EXECUTOR = "pushDispatchExecutor"
        private const val WORKER_COUNT = 4
        private const val QUEUE_CAPACITY = 1_000
        private const val SHUTDOWN_TIMEOUT_SECONDS = 10
        private const val DISCARD_LOG_INTERVAL = 100L
        private val log = LoggerFactory.getLogger(AlarmAsyncConfig::class.java)
    }
}
