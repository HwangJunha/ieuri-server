package org.around.ieuri.auth.infrastructure.batch

import org.around.ieuri.auth.application.port.`in`.command.AuditRetentionUseCase
import org.around.ieuri.auth.infrastructure.config.AuditRetentionProperties
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
import org.springframework.transaction.PlatformTransactionManager

/**
 * 회원 감사 로그 보관 기간 만료 삭제 잡(SPEC-0032 R22, POL-014/POL-044).
 *
 * `messageRetentionJob`과 같은 형태다 — 인라인 Tasklet, 회차 단위 반복, 건수만 로그.
 *
 * **실행 시점은 k8s CronJob이 소유한다**(POL-056). 등록하지 않으면 배치가 조용히 실행되지
 * 않고 정책은 여전히 미집행으로 남는다. `ieuri-manifest`는 이 저장소 밖이므로 매니페스트
 * 반영은 별도로 이루어져야 한다:
 *
 *     IEURI_TOUR_SYNC_JOB=auditRetentionJob
 */
@Configuration
@Profile("batch")
class AuditRetentionJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val retentionUseCase: AuditRetentionUseCase,
    private val properties: AuditRetentionProperties,
) {

    @Bean
    fun auditRetentionJob(): Job {
        val step = StepBuilder("${JOB_NAME}Step", jobRepository)
            .tasklet(purgeTasklet(), transactionManager)
            .build()
        return JobBuilder(JOB_NAME, jobRepository).start(step).build()
    }

    private fun purgeTasklet() = Tasklet { _, _ ->
        var totalDeleted = 0
        var batches = 0
        while (batches < properties.maxBatchesPerRun) {
            val deleted = retentionUseCase.purgeExpiredBatch(properties.batchSize)
            totalDeleted += deleted
            batches += 1
            // 상한보다 적게 지웠으면 더 지울 것이 없다.
            if (deleted < properties.batchSize) break
        }
        // 개인정보를 담지 않는다(POL-013) — 건수만 남긴다.
        log.info("expired audit logs purged: total={} batches={}", totalDeleted, batches)
        RepeatStatus.FINISHED
    }

    private companion object {
        private val log = LoggerFactory.getLogger(AuditRetentionJobConfig::class.java)
        const val JOB_NAME = "auditRetentionJob"
    }
}
