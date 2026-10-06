package org.around.ieuri.tourism.adapter.batch

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
 * 동기화 잡 실행의 단일 진입점. batch 프로파일에서만 생성되며 잡은 `--ieuri.tourapi.sync.job`으로 고른다.
 * - 같은 실행일 = 같은 JobInstance라 중복 실행은 [JobInstanceAlreadyCompleteException]·[JobExecutionAlreadyRunningException]으로 막힌다.
 * - 단일 인스턴스 전제다. 다중 배포 시에는 분산 락이 필요하다.
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

    /** 부팅 즉시 1회 실행하고 프로세스를 종료한다 — 실행 시점은 k8s CronJob이 소유한다. */
    override fun run(args: ApplicationArguments) {
        exitCode = if (launch()) 0 else 1
        exitProcess(SpringApplication.exit(context))
    }

    /** 잡이 실패로 끝나면 1. CronJob의 재시도·알림이 이 값에 걸린다. */
    override fun getExitCode(): Int = exitCode

    /**
     * 설정된 잡을 실행일 파라미터와 함께 1회 실행한다.
     *
     * @return 성공(또는 중복 스킵)이면 true, `JobExecution.status=FAILED`면 false.
     */
    fun launch(): Boolean {
        val job = jobs.firstOrNull { it.name == properties.job }
            ?: error("잡을 찾을 수 없습니다: ${properties.job} (등록된 잡: ${jobs.map { it.name }})")
        val runDate = LocalDate.now(ZoneId.of(properties.zone))
        val parameters = JobParametersBuilder()
            // 식별 파라미터이자 Step들의 실행일이다 — Step 안에서 now()를 부르면 안 된다.
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
            // 중복 차단은 정상이므로 성공으로 본다.
            log.info("{} already completed for {} — skipping", job.name, runDate)
            true
        } catch (e: JobExecutionAlreadyRunningException) {
            log.warn("{} still running for {} — skipping this trigger", job.name, runDate)
            true
        }
    }
}
