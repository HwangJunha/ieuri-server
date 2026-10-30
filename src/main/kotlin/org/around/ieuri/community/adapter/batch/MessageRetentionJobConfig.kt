package org.around.ieuri.community.adapter.batch

import org.around.ieuri.community.adapter.config.MessageRetentionProperties
import org.around.ieuri.community.application.conversation.provided.MessageRetentionUseCase
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
 * DM 메시지 보관 기간 만료 삭제 잡(SPEC-0017 R16, POL-050). 실행 시점은 k8s CronJob이 정한다.
 * - 트랜잭션은 회차마다 유스케이스가 연다. 스텝에 매니저를 넘기지 않는다 — 넘기면 태스클릿 전체가 한 트랜잭션으로 묶인다.
 */
@Configuration
@Profile("batch")
class MessageRetentionJobConfig(
    private val jobRepository: JobRepository,
    private val retentionUseCase: MessageRetentionUseCase,
    private val properties: MessageRetentionProperties,
) {

    @Bean
    fun messageRetentionJob(): Job {
        val step = StepBuilder("${JOB_NAME}Step", jobRepository)
            .tasklet(purgeTasklet())
            .build()
        return JobBuilder(JOB_NAME, jobRepository).start(step).build()
    }

    /** 상한보다 적게 지운 회차가 나오거나 최대 회차에 이르면 멈춘다. 남은 것은 다음 실행이 지운다. */
    private fun purgeTasklet() = Tasklet { _, _ ->
        var totalDeleted = 0
        var batches = 0
        while (batches < properties.maxBatchesPerRun) {
            val deleted = retentionUseCase.purgeExpiredBatch(properties.batchSize)
            totalDeleted += deleted
            batches += 1
            if (deleted < properties.batchSize) break
        }
        // 건수만 남긴다(POL-013).
        log.info("expired DM messages purged: total={} batches={}", totalDeleted, batches)
        RepeatStatus.FINISHED
    }

    private companion object {
        private val log = LoggerFactory.getLogger(MessageRetentionJobConfig::class.java)

        const val JOB_NAME = "messageRetentionJob"
    }
}
