package org.around.ieuri.tourism.infrastructure.batch

import org.slf4j.LoggerFactory
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException
import org.springframework.batch.core.launch.JobOperator
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.ExitCodeGenerator
import org.springframework.boot.SpringApplication
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneId
import kotlin.system.exitProcess

/**
 * 동기화 잡 실행의 단일 진입점. batch 프로파일에서만 생성되며, 실행할 잡은 외부가 정한다
 * (`--ieuri.tourapi.sync.job=spotSyncJob`).
 *
 * 중복 실행 방지는 Spring Batch의 `JobInstance` 유일성에 맡긴다 — 실행 일자를 식별 파라미터로 넣어
 * "같은 날짜 = 같은 JobInstance"로 막는다. 이미 완료된 날짜면 [JobInstanceAlreadyCompleteException],
 * 실행 중이면 [JobExecutionAlreadyRunningException]이 나며 둘 다 정상적인 중복 차단이다.
 *
 * > 단일 인스턴스 전제다. 다중 배포 시에는 분산 락이 추가로 필요하다.
 */
@Component
@Profile(BATCH_PROFILE)
class SpotSyncLauncher(
    private val jobOperator: JobOperator,
    private val jobs: List<Job>,
    private val properties: SpotSyncProperties,
    private val context: ApplicationContext,
) : ApplicationRunner,
    ExitCodeGenerator {

    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var exitCode = 0

    /**
     * 부팅 즉시 1회 실행하고 프로세스를 종료한다 — 실행 시점은 외부(k8s CronJob)가 소유한다.
     * [SpringApplication.exit]가 컨텍스트를 닫아 커넥션 풀·스레드를 정리한 뒤 종료 코드를 계산한다.
     */
    override fun run(args: ApplicationArguments) {
        exitCode = if (launch()) 0 else 1
        exitProcess(SpringApplication.exit(context))
    }

    /** 잡이 실패로 끝나면 1. CronJob의 재시도·알림이 이 값에 걸린다. */
    override fun getExitCode(): Int = exitCode

    /**
     * 설정된 잡을 실행일 파라미터와 함께 1회 실행한다.
     *
     * @return 성공(또는 중복 스킵)이면 true, 잡이 실패로 끝났으면 false. Spring Batch는 스텝 실패를
     *   예외로 던지지 않고 `JobExecution.status=FAILED`로 표현하므로, 이 상태를 보지 않으면 실패한
     *   실행을 성공으로 착각한다.
     */
    fun launch(): Boolean {
        val job = jobs.firstOrNull { it.name == properties.job }
            ?: error("잡을 찾을 수 없습니다: ${properties.job} (등록된 잡: ${jobs.map { it.name }})")
        val runDate = LocalDate.now(ZoneId.of(properties.zone))
        val parameters = JobParametersBuilder()
            // 식별 파라미터. 같은 날짜 = 같은 JobInstance = 중복 실행 불가.
            // Step들이 이 값을 실행일로 읽으므로 Step 안에서 now()를 부르면 안 된다 — 자정을 걸쳐
            // 도는 실행에서 단계마다 날짜가 달라진다.
            .addString(RUN_DATE_PARAMETER, runDate.toString())
            .toJobParameters()

        return try {
            val execution = jobOperator.start(job, parameters)
            val failed = execution.status.isUnsuccessful
            if (failed) {
                log.error("{} FAILED: runDate={} status={}", job.name, runDate, execution.status)
            } else {
                log.info("{} finished: runDate={} status={}", job.name, runDate, execution.status)
            }
            !failed
        } catch (e: JobInstanceAlreadyCompleteException) {
            // 중복 차단은 정상이므로 성공으로 본다 — CronJob이 실패로 알람을 울릴 이유가 없다.
            log.info("{} already completed for {} — skipping", job.name, runDate)
            true
        } catch (e: JobExecutionAlreadyRunningException) {
            log.warn("{} still running for {} — skipping this trigger", job.name, runDate)
            true
        }
    }
}
