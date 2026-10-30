package org.around.ieuri.auth.adapter.config
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

/**
 * 접속기록 저장 전용 executor(도메인별 격리).
 * - 큐가 차도 폐기하지 않는다. `CallerRunsPolicy`로 호출 스레드가 직접 저장한다 — 법정 기록이라 유실이 허용되지 않는다.
 */
@Configuration
@EnableAsync
class AuthAsyncConfig {

    @Bean(ACCESS_LOG_EXECUTOR)
    fun accessLogExecutor(): Executor = ThreadPoolTaskExecutor().apply {
        corePoolSize = WORKER_COUNT
        maxPoolSize = WORKER_COUNT
        queueCapacity = QUEUE_CAPACITY
        setThreadNamePrefix("access-log-")

        // 종료 시 큐에 남은 기록을 마저 저장한다 — 배포 중에 기록이 사라지면 안 된다.
        setWaitForTasksToCompleteOnShutdown(true)
        setAwaitTerminationSeconds(SHUTDOWN_TIMEOUT_SECONDS)
        initialize()
    }

    companion object {
        const val ACCESS_LOG_EXECUTOR = "accessLogExecutor"
        private const val WORKER_COUNT = 2
        private const val QUEUE_CAPACITY = 500
        private const val SHUTDOWN_TIMEOUT_SECONDS = 10
    }
}
