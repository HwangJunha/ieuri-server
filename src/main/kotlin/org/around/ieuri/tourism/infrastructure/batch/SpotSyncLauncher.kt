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
 * 동기화 잡 실행의 단일 진입점(SPEC-0004 AC-4·AC-6). **batch 프로파일에서만 생성된다** —
 * API 서버 프로세스에는 이 빈도, [SpotSyncJobConfig]의 잡도 존재하지 않는다.
 *
 * **실행 시점도, 실행할 잡도 외부가 정한다**: `--ieuri.tourapi.sync.job=spotSyncJob`(기본값).
 * 잡이 늘어나도 이 클래스는 그대로 두고 인자만 바꾸면 되며, CronJob 매니페스트가
 * "무엇을 돌릴지"를 소유한다.
 *
 * **Boot의 `JobLauncherApplicationRunner`를 쓰지 않는 이유는 잡 파라미터 때문이다.**
 * 그쪽은 커맨드라인 non-option args로만 파라미터를 받는데, 이 잡은 **실행 일자**를 식별
 * 파라미터로 넣어야 한다(같은 날짜 = 같은 JobInstance = 중복 실행 불가, AC-6). 날짜를 CronJob
 * 매니페스트에 하드코딩할 수 없으므로 실행 시점에 서버가 만든다. 그 한 가지를 빼면 나머지는
 * Boot 방식과 같다 — [ApplicationRunner]·[ExitCodeGenerator]를 그대로 구현한다.
 *
 * **중복 실행 방지는 Spring Batch의 `JobInstance` 유일성에 맡긴다**(설계 문서 §4.6).
 * 이미 완료된 날짜면 [JobInstanceAlreadyCompleteException], 실행 중이면
 * [JobExecutionAlreadyRunningException]이 나며 **둘 다 오류가 아니라 정상적인 중복 차단**이다.
 *
 * > 단일 인스턴스 전제다. 다중으로 배포하면 두 인스턴스가 동시에 발화할 때 경합이 생길 수 있어
 * > Valkey 분산 락이 추가로 필요하다(설계 문서 §4.6). k3s CronJob은 `concurrencyPolicy: Forbid`로
 * > 파드 수준에서 한 번 더 막는다.
 */
@Component
@Profile(BATCH_PROFILE)
class SpotSyncLauncher(
    private val jobOperator: JobOperator,
    private val jobs: List<Job>,
    private val properties: SpotSyncProperties,
    private val context: ApplicationContext,
) : ApplicationRunner, ExitCodeGenerator {

    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var exitCode = 0

    /**
     * 부팅 즉시 1회 실행하고 프로세스를 종료한다. **실행 시점은 외부(k8s CronJob)가 소유한다** —
     * 앱 내부에 스케줄러를 두지 않으므로 트리거가 하나뿐이고, 하루 두 번 도는 실수가 원천 차단된다.
     *
     * CronJob은 "파드가 뜨고 → 잡이 끝나면 종료되는" 모델이라 명시적으로 종료해야 한다.
     * [SpringApplication.exit]가 컨텍스트를 닫아 커넥션 풀·스레드를 정리한 뒤 종료 코드를
     * 계산하며, 그 값이 [getExitCode]에서 온다.
     */
    override fun run(args: ApplicationArguments) {
        exitCode = if (launch()) 0 else 1
        exitProcess(SpringApplication.exit(context))
    }

    /** 잡이 실패로 끝나면 1. CronJob의 재시도·알림이 이 값에 걸린다. */
    override fun getExitCode(): Int = exitCode

    /**
     * @return 성공(또는 중복 스킵)이면 true, **잡이 실패로 끝났으면 false**.
     *
     * 실패를 예외가 아니라 반환값으로 알리는 이유는, Spring Batch가 스텝 실패를 예외로
     * 던지지 않고 `JobExecution.status=FAILED`로 표현하기 때문이다. 이걸 보지 않으면
     * 실패한 실행을 성공으로 착각한다(부분 실패는 R-3상 정상이지만, 그것과
     * "잡 전체가 실패로 끝남"은 다르다).
     */
    fun launch(): Boolean {
        val job = jobs.firstOrNull { it.name == properties.job }
            ?: error("잡을 찾을 수 없습니다: ${properties.job} (등록된 잡: ${jobs.map { it.name }})")
        val runDate = LocalDate.now(ZoneId.of(properties.zone))
        val parameters = JobParametersBuilder()
            // 식별 파라미터. 같은 날짜 = 같은 JobInstance = 중복 실행 불가(AC-6).
            // **축제 Step이 이 값을 실행일로 읽는다**(SPEC-0007 AC-8) — Step 안에서 now()를 부르지
            // 않으므로 수집 조건·종료 판정·재삽입 제외가 모두 같은 날짜를 쓴다.
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
