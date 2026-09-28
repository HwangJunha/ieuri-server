package org.around.ieuri.alarm.infrastructure.config

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicLong

/**
 * 푸시 발송 전용 executor. community의 조회수 executor, tourism의 write-behind와 같은 관례다.
 *
 * **도메인마다 executor를 따로 두는 이유는 격리다.** 발송이 밀렸을 때 조회수 증가가 함께
 * 굶으면 안 된다.
 *
 * 워커가 조회수 쪽보다 많은 이유는 작업의 성격이 다르기 때문이다 — UPDATE 한 문장이 아니라
 * FCM으로 나가는 블로킹 HTTP 왕복이라 한 건이 붙들고 있는 시간이 길다.
 *
 * 큐가 차면 **폐기한다.** 푸시는 유실 허용이고(POL-048) 재시도하지 않는다(AC12.3) —
 * 알림 한 건을 잃는 것이 메시지 전송을 늦추거나 스레드를 무한정 쌓는 것보다 낫다.
 */
@Configuration
@EnableAsync
class AlarmAsyncConfig {

    /** 폐기 건수. 꾸준히 늘면 큐 상한이나 워커 수가 부족하다는 신호다. */
    private val discarded = AtomicLong()

    @Bean(PUSH_EXECUTOR)
    fun pushDispatchExecutor(): Executor = ThreadPoolTaskExecutor().apply {
        corePoolSize = WORKER_COUNT
        maxPoolSize = WORKER_COUNT
        queueCapacity = QUEUE_CAPACITY
        setThreadNamePrefix("push-dispatcher-")

        setRejectedExecutionHandler { _, _ ->
            val count = discarded.incrementAndGet()
            // 폐기가 잦으면 로그가 폭주하므로 표본만 남긴다. 정확한 총계는 카운터가 갖는다.
            if (count % DISCARD_LOG_INTERVAL == 1L) {
                log.warn("push dispatch discarded (total={})", count)
            }
        }

        setWaitForTasksToCompleteOnShutdown(true)
        setAwaitTerminationSeconds(SHUTDOWN_TIMEOUT_SECONDS)
        initialize()
    }

    /** 관측용. 폐기 총계를 외부에서 확인할 수 있게 노출한다. */
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
