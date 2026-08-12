package org.around.ieuri.community.infrastructure.batch

import org.around.ieuri.community.application.port.`in`.command.MessageRetentionUseCase
import org.around.ieuri.community.infrastructure.config.MessageRetentionProperties
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
 * DM 메시지 보관 기간 만료 삭제 잡(SPEC-0017 R16, POL-050).
 *
 * **회차를 나눠 지운다.** 6개월치가 한 번에 쌓인 상태에서 단일 트랜잭션으로 지우면 락과 WAL이
 * 함께 커진다. 회차당 상한과 실행당 최대 회차 수는 설정으로 뺐다(POL-044) — 실측 전까지
 * 확정값이 아니므로 근거 없는 계수를 코드에 박지 않는다.
 *
 * 실행 시점은 k8s CronJob이 소유한다(기존 tourism 잡과 같은 방식).
 *
 * 트랜잭션 경계는 **회차마다** 끊긴다 — 유스케이스가 @Transactional이고 태스클릿은 그것을
 * 반복 호출할 뿐이다. 태스클릿에 트랜잭션을 걸면 회차를 나눈 의미가 없어진다.
 */
@Configuration
@Profile("batch")
class MessageRetentionJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val retentionUseCase: MessageRetentionUseCase,
    private val properties: MessageRetentionProperties,
) {

    @Bean
    fun messageRetentionJob(): Job {
        val step = StepBuilder("${JOB_NAME}Step", jobRepository)
            .tasklet(purgeTasklet(), transactionManager)
            .build()
        return JobBuilder(JOB_NAME, jobRepository).start(step).build()
    }

    /**
     * 상한보다 적게 지운 회차가 나오면 **지울 것이 없다는 뜻이라 멈춘다.** 실행당 최대 회차
     * 수는 무한 루프를 막는 상한이며, 남은 것은 다음 실행이 이어 지운다.
     */
    private fun purgeTasklet() = Tasklet { _, _ ->
        var totalDeleted = 0
        var batches = 0
        while (batches < properties.maxBatchesPerRun) {
            val deleted = retentionUseCase.purgeExpiredBatch(properties.batchSize)
            totalDeleted += deleted
            batches += 1
            if (deleted < properties.batchSize) break
        }
        // 개인정보를 담지 않는다(POL-013) — 건수만 남긴다.
        log.info("expired DM messages purged: total={} batches={}", totalDeleted, batches)
        RepeatStatus.FINISHED
    }

    private companion object {
        private val log = LoggerFactory.getLogger(MessageRetentionJobConfig::class.java)

        const val JOB_NAME = "messageRetentionJob"
    }
}
