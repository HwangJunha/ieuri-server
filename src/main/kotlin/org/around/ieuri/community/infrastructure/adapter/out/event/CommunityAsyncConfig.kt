package org.around.ieuri.community.infrastructure.adapter.out.event

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicLong

/**
 * 조회수 증가 전용 executor. tourism의 write-behind executor와 같은 관례다.
 *
 * 유계 큐를 쓰고 큐가 차면 작업을 **폐기**한다. 조회수는 근사치이고, 한 건을 잃는 것이
 * 조회 응답을 늦추거나 스레드를 무한정 쌓는 것보다 낫다.
 */
@Configuration
@EnableAsync
class CommunityAsyncConfig {

    /** 폐기 건수. 꾸준히 늘면 큐 상한이나 워커 수가 부족하다는 신호다. */
    private val discarded = AtomicLong()

    @Bean(VIEW_COUNT_EXECUTOR)
    fun postViewCountExecutor(): Executor = ThreadPoolTaskExecutor().apply {
        // UPDATE 한 문장이라 소수로 충분하다. 늘리면 DB 커넥션 풀을 잠식한다.
        corePoolSize = WORKER_COUNT
        maxPoolSize = WORKER_COUNT
        queueCapacity = QUEUE_CAPACITY
        setThreadNamePrefix("post-view-counter-")

        setRejectedExecutionHandler { _, _ ->
            val count = discarded.incrementAndGet()
            // 폐기가 잦으면 로그가 폭주하므로 표본만 남긴다. 정확한 총계는 카운터가 갖는다.
            if (count % DISCARD_LOG_INTERVAL == 1L) {
                log.warn("post view count discarded (total={})", count)
            }
        }

        setWaitForTasksToCompleteOnShutdown(true)
        setAwaitTerminationSeconds(SHUTDOWN_TIMEOUT_SECONDS)
        initialize()
    }

    /** 관측용. 폐기 총계를 외부에서 확인할 수 있게 노출한다. */
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
