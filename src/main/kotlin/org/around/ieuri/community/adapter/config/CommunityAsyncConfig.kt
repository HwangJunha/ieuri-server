package org.around.ieuri.community.adapter.config

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.atomic.AtomicLong

/**
 * 커뮤니티 비동기 executor(도메인별 격리).
 * - 조회수: 유계 큐가 차면 작업을 폐기한다(조회수는 근사치).
 * - 탈퇴 정리: 폐기하지 않는다. 큐가 차면 호출 스레드가 직접 지운다 — 개인정보 파기라 유실이 허용되지 않는다.
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

    @Bean(WITHDRAWAL_EXECUTOR)
    fun memberWithdrawalExecutor(): Executor = ThreadPoolTaskExecutor().apply {
        corePoolSize = WITHDRAWAL_WORKER_COUNT
        maxPoolSize = WITHDRAWAL_WORKER_COUNT
        queueCapacity = QUEUE_CAPACITY
        setThreadNamePrefix("community-withdrawal-")
        setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())

        // 종료 시 큐에 남은 정리를 마저 한다 — 배포 중에 파기가 빠지면 안 된다.
        setWaitForTasksToCompleteOnShutdown(true)
        setAwaitTerminationSeconds(SHUTDOWN_TIMEOUT_SECONDS)
        initialize()
    }

    /** 폐기 총계. */
    fun discardedCount(): Long = discarded.get()

    companion object {
        const val VIEW_COUNT_EXECUTOR = "postViewCountExecutor"
        const val WITHDRAWAL_EXECUTOR = "communityWithdrawalExecutor"
        private const val WORKER_COUNT = 2
        private const val WITHDRAWAL_WORKER_COUNT = 1
        private const val QUEUE_CAPACITY = 1_000
        private const val SHUTDOWN_TIMEOUT_SECONDS = 10
        private const val DISCARD_LOG_INTERVAL = 100L
        private val log = LoggerFactory.getLogger(CommunityAsyncConfig::class.java)
    }
}
