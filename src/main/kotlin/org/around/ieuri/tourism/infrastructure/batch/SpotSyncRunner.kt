package org.around.ieuri.tourism.infrastructure.batch

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.ExitCodeGenerator
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import kotlin.system.exitProcess

/**
 * 외부 트리거(k3s CronJob) 모드(SPEC-0004 R-2). **부팅 즉시 1회 실행하고 프로세스를 종료한다.**
 *
 * CronJob은 "파드를 띄우고 → 잡이 끝나면 파드가 종료되는" 모델이라, 앱이 계속 떠 있으면
 * 잡이 끝나도 파드가 Completed로 넘어가지 않는다. 그래서 실행 후 명시적으로 종료한다.
 *
 * **종료 코드로 성패를 알린다** — CronJob의 재시도·알림이 여기에 걸린다:
 *  - 0 : 성공, 또는 중복이라 건너뜀(오류가 아니다)
 *  - 1 : 잡이 실패로 끝남
 *
 * 스케줄러 모드([SpotSyncScheduler])와는 배타적이다. `scheduler-enabled=true`면 이 빈은 뜨지 않고,
 * 앱이 떠 있는 채로 cron 시각을 기다린다.
 */
@Component
// 조건이 둘이라 @ConditionalOnProperty를 겹쳐 쓸 수 없다(같은 애노테이션 반복 불가) —
// enabled 게이트는 별도 클래스로 뽑아 AND로 결합한다. enabled=false인 API 서버에서
// 이 빈이 살아나면 SpotSyncLauncher가 없어 주입 실패로 부팅이 깨진다.
@ConditionalOnBatchTrigger
class SpotSyncRunner(
    private val launcher: SpotSyncLauncher,
    private val context: ApplicationContext,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val exitCode = try {
            // 잡이 FAILED로 끝나면 launch()가 false를 준다 — 예외가 아니라 상태로 오므로
            // 반환값을 봐야 CronJob이 실패를 인지한다.
            if (launcher.launch()) 0 else 1
        } catch (e: Exception) {
            // 잡 자체의 실패(페이지 전량 실패 등)는 여기까지 올라온다. 스택트레이스를 남겨야
            // CronJob 로그만 보고도 원인을 알 수 있다.
            log.error("spot sync failed", e)
            1
        }
        // 컨텍스트를 닫아 커넥션 풀·스레드를 정리한 뒤 종료한다. 바로 exitProcess를 부르면
        // 진행 중이던 트랜잭션이 정리되지 않을 수 있다.
        exitProcess(org.springframework.boot.SpringApplication.exit(context, ExitCodeGenerator { exitCode }))
    }
}
