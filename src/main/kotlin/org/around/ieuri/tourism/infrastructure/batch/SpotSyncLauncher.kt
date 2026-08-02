package org.around.ieuri.tourism.infrastructure.batch

import org.slf4j.LoggerFactory
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException
import org.springframework.batch.core.launch.JobOperator
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneId

/**
 * 동기화 잡 실행의 단일 진입점(SPEC-0004 AC-4·AC-6). 트리거가 스케줄러든 CronJob이든
 * **중복 실행 방지 규칙은 한 곳에만 둔다** — 트리거별로 복사하면 한쪽만 고쳐지기 쉽다.
 *
 * **중복 실행 방지는 Spring Batch의 `JobInstance` 유일성에 맡긴다**(설계 문서 §4.6).
 * 잡 파라미터에 실행 일자를 넣으면 같은 날짜로는 두 번 실행되지 않으므로 하루 1회가
 * 자연스럽게 강제된다. 이미 완료된 날짜면 [JobInstanceAlreadyCompleteException], 실행 중이면
 * [JobExecutionAlreadyRunningException]이 나며 **둘 다 오류가 아니라 정상적인 중복 차단**이다.
 *
 * > 단일 인스턴스 전제다. 다중으로 배포하면 두 인스턴스가 동시에 발화할 때 경합이 생길 수 있어
 * > Valkey 분산 락이 추가로 필요하다(설계 문서 §4.6). k3s CronJob은 `concurrencyPolicy: Forbid`로
 * > 파드 수준에서 한 번 더 막는다.
 */
@Component
@ConditionalOnProperty(prefix = "ieuri.tourapi.sync", name = ["enabled"], havingValue = "true")
class SpotSyncLauncher(
    private val jobOperator: JobOperator,
    private val spotSyncJob: Job,
    private val properties: SpotSyncProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * @return 성공(또는 중복 스킵)이면 true, **잡이 실패로 끝났으면 false**.
     *
     * 실패를 예외가 아니라 반환값으로 알리는 이유는, Spring Batch가 스텝 실패를 예외로
     * 던지지 않고 `JobExecution.status=FAILED`로 표현하기 때문이다. 이걸 보지 않으면
     * CronJob이 실패한 실행을 성공으로 착각한다(부분 실패는 R-3상 정상이지만, 그것과
     * "잡 전체가 실패로 끝남"은 다르다).
     */
    fun launch(): Boolean {
        val runDate = LocalDate.now(ZoneId.of(properties.zone))
        val parameters = JobParametersBuilder()
            // 식별 파라미터. 같은 날짜 = 같은 JobInstance = 중복 실행 불가(AC-6).
            .addString("runDate", runDate.toString())
            .toJobParameters()

        return try {
            val execution = jobOperator.start(spotSyncJob, parameters)
            val failed = execution.status.isUnsuccessful
            if (failed) {
                log.error("spot sync FAILED: runDate={} status={}", runDate, execution.status)
            } else {
                log.info("spot sync finished: runDate={} status={}", runDate, execution.status)
            }
            !failed
        } catch (e: JobInstanceAlreadyCompleteException) {
            log.info("spot sync already completed for {} — skipping", runDate)
            false
        } catch (e: JobExecutionAlreadyRunningException) {
            log.warn("spot sync still running for {} — skipping this trigger", runDate)
            false
        }
    }
}
