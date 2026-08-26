package org.around.ieuri.auth.infrastructure.config

import java.util.concurrent.Executor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

/**
 * 접속기록 저장 전용 executor. alarm의 푸시, community의 조회수와 같은 관례다.
 *
 * **도메인마다 executor를 따로 두는 이유는 격리다** — 푸시가 밀렸을 때 접속기록이 함께 굶으면
 * 안 되고, 그 반대도 마찬가지다.
 *
 * 워커가 적은 이유는 발행량이 적기 때문이다. 기록 대상이 로그인·탈퇴 등 계정 사건뿐이라
 * (`AccessType`) 조회 API처럼 초당 수십 건이 들어오지 않는다.
 *
 * **큐가 차도 폐기하지 않는다.** 푸시(POL-048)와 다른 점이다 — 접속기록은 법정 기록이라
 * 유실이 허용되지 않는다. `CallerRunsPolicy`가 기본값이므로 큐가 차면 **호출 스레드가 직접
 * 저장한다.** 그러면 그 요청만 느려지고 기록은 남는다. 폐기하면 침해사고 조사에서 그 구간이
 * 통째로 비는데, 그것이 느려지는 것보다 나쁘다.
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
