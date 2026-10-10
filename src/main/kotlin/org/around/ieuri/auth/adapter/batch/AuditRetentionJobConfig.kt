package org.around.ieuri.auth.adapter.batch
import org.around.ieuri.auth.adapter.config.AuditRetentionProperties
import org.around.ieuri.auth.application.audit.provided.AuditRetentionUseCase
import org.slf4j.LoggerFactory
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * 감사 기록 보관 기간 만료 삭제 잡(SPEC-0032 R22, POL-014/POL-044). 회차 단위 반복, 건수만 로그.
 * - 실행 시점은 k8s CronJob이 소유한다(POL-056). `ieuri-manifest`에 등록하지 않으면 실행되지 않는다:
 *     IEURI_TOUR_SYNC_JOB=auditRetentionJob
 */
@Configuration
@Profile("batch")
class AuditRetentionJobConfig(
    private val jobRepository: JobRepository,
    private val retentionUseCase: AuditRetentionUseCase,
    private val properties: AuditRetentionProperties,
) {

    /** 대상별로 스텝을 나눈다. 한 tasklet이면 앞 대상의 실패가 뒤 대상(법정 6개월)의 삭제를 막는다. */
    @Bean
    fun auditRetentionJob(): Job {
        val memberAuditStep = StepBuilder("${JOB_NAME}Step", jobRepository)
            .tasklet(purgeTasklet("member audit logs", retentionUseCase::purgeExpiredBatch))
            .build()
        val locationAccessStep = StepBuilder("${JOB_NAME}LocationAccessStep", jobRepository)
            .tasklet(purgeTasklet("location access logs", retentionUseCase::purgeExpiredLocationAccessBatch))
            .build()
        val accessLogStep = StepBuilder("${JOB_NAME}AccessLogStep", jobRepository)
            .tasklet(purgeTasklet("access logs", retentionUseCase::purgeExpiredAccessLogBatch))
            .build()
        return JobBuilder(JOB_NAME, jobRepository)
            .start(memberAuditStep)
            .next(locationAccessStep)
            .next(accessLogStep)
            .build()
    }

    /** 회차 반복은 대상과 무관하게 같다. 유스케이스 호출 한 번이 한 회차이고 회차마다 커밋한다. */
    private fun purgeTasklet(label: String, purgeBatch: (Int) -> Int) = Tasklet { _, _ ->
        var totalDeleted = 0
        var batches = 0
        while (batches < properties.maxBatchesPerRun) {
            val deleted = purgeBatch(properties.batchSize)
            totalDeleted += deleted
            batches += 1
            // 상한보다 적게 지웠으면 더 지울 것이 없다.
            if (deleted < properties.batchSize) break
        }
        // 개인정보를 담지 않는다(POL-013) — 건수만 남긴다.
        log.info("expired {} purged: total={} batches={}", label, totalDeleted, batches)
        RepeatStatus.FINISHED
    }

    private companion object {
        private val log = LoggerFactory.getLogger(AuditRetentionJobConfig::class.java)
        const val JOB_NAME = "auditRetentionJob"
    }
}
