package org.around.ieuri.tourism.infrastructure.batch

import org.slf4j.LoggerFactory
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.launch.JobOperator
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneId

/**
 * 관광지 동기화 잡 스케줄러(SPEC-0004 R-2·AC-4·AC-6).
 *
 * **중복 실행 방지는 Spring Batch의 `JobInstance` 유일성에 맡긴다**(설계 문서 §4.6). 잡 파라미터에
 * 실행 일자를 넣으면 같은 날짜로는 두 번 실행되지 않으므로 하루 1회가 자연스럽게 강제된다(AC-4·AC-6).
 * 이미 완료된 날짜로 재실행하면 [JobInstanceAlreadyCompleteException], 실행 중이면
 * [JobExecutionAlreadyRunningException]이 나며, 둘 다 오류가 아니라 **정상적인 중복 차단**이므로
 * 로그만 남기고 삼킨다.
 *
 * > **다중 인스턴스로 배포하면 이것으로 부족하다.** 두 인스턴스가 동시에 스케줄을 발화시키면 경합이
 * > 생길 수 있다. 현재 배포 형상이 단일이라는 전제이며, 다중으로 가면 Valkey 분산 락으로 게이트해야
 * > 한다(이미 Valkey를 쓰고 있으므로 추가 의존이 가장 적다). 설계 문서 §4.6에 남긴 미확인 항목이다.
 *
 * 기본적으로 꺼져 있다([SpotSyncProperties.enabled]) — 배치가 upstream 호출 한도를 소모하기 때문이다.
 */
@Component
@ConditionalOnProperty(prefix = "ieuri.tourapi.sync", name = ["enabled"], havingValue = "true")
class SpotSyncScheduler(
    private val jobOperator: JobOperator,
    private val spotSyncJob: Job,
    private val properties: SpotSyncProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${ieuri.tourapi.sync.cron}", zone = "\${ieuri.tourapi.sync.zone}")
    fun run() {
        val runDate = LocalDate.now(ZoneId.of(properties.zone))
        val parameters = JobParametersBuilder()
            // 식별 파라미터. 같은 날짜 = 같은 JobInstance = 중복 실행 불가(AC-6).
            .addString("runDate", runDate.toString())
            .toJobParameters()

        try {
            jobOperator.start(spotSyncJob, parameters)
        } catch (e: JobInstanceAlreadyCompleteException) {
            log.info("spot sync already completed for {} — skipping", runDate)
        } catch (e: JobExecutionAlreadyRunningException) {
            log.warn("spot sync still running for {} — skipping this trigger", runDate)
        }
    }
}
