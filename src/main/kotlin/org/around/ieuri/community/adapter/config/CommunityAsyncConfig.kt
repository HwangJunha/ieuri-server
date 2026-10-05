package org.around.ieuri.community.adapter.config

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicLong

/**
 * 조회수 증가 전용 executor.
 * - 유계 큐가 차면 작업을 폐기한다(조회수는 근사치).
 */
@Configuration
@EnableAsync
class CommunityAsyncConfig {

    /** 폐기 건수. */
    private val discarded = AtomicLong()

    @Bean(VIEW_COUNT_EXECUTOR)
    fun postViewCountExecutor(): Executor = ThreadPoolTaskExecutor().apply {
        // 늘리면 DB 커넥션 풀을 잠식한다.
        corePoolSize = WORKER_COUNT
        maxPoolSize = WORKER_COUNT
        queueCapacity = QUEUE_CAPACITY
        setThreadNamePrefix("post-view-counter-")

        setRejectedExecutionHandler { _, _ ->
            val count = discarded.incrementAndGet()
            // 로그는 표본만 남긴다. 총계는 카운터가 갖는다.
            if (count % DISCARD_LOG_INTERVAL == 1L) {
                log.warn("post view count discarded (total={})", count)
            }
        }

        setWaitForTasksToCompleteOnShutdown(true)
        setAwaitTerminationSeconds(SHUTDOWN_TIMEOUT_SECONDS)
        initialize()
    }

    /** 폐기 총계. */
    fun discardedCount(): Long = discarded.get()

    companion object {
        const val VIEW_COUNT_EXECUTOR = "postViewCountExecutor"
        private const val WORKER_COUNT = 2
        private const val QUEUE_CAPACITY = 1_000
        private const val SHUTDOWN_TIMEOUT_SECONDS = 10
        private const val DISCARD_LOG_INTERVAL = 100L
        private val log = LoggerFactory.getLogger(CommunityAsyncConfig::class.java)
    }
}
