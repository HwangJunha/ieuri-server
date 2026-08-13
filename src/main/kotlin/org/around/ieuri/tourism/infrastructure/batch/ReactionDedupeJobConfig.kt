package org.around.ieuri.tourism.infrastructure.batch

import org.around.ieuri.tourism.application.port.`in`.command.ReactionDedupeUseCase
import org.slf4j.LoggerFactory
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.transaction.PlatformTransactionManager

/**
 * 좋아요 dedupe 잡(SPEC-0019 §4.2). **일회성이며 참조 축 이전 중 한 번만 돈다.**
 *
 * 실행 순서가 중요하다:
 * ```
 * V53 (컬럼 추가 + 백필)  →  이 잡  →  V54 (NOT NULL + UNIQUE 교체)
 * ```
 * 순서가 뒤집히면 V54의 `ADD CONSTRAINT`가 중복 때문에 실패한다.
 *
 * 일 배치(spotSyncJob)에 넣지 않은 이유는 이 작업이 매일 할 일이 아니기 때문이다 —
 * 이전이 끝나면 `UNIQUE(member_id, catalog_id)`가 중복 발생 자체를 막는다.
 * `--ieuri.tourapi.sync.job=reactionDedupeJob`으로 실행한다.
 */
@Configuration
@Profile(BATCH_PROFILE)
class ReactionDedupeJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val useCase: ReactionDedupeUseCase,
) {

    @Bean
    fun reactionDedupeJob(): Job =
        JobBuilder(JOB_NAME, jobRepository)
            .start(dedupeStep())
            .build()

    private fun dedupeStep(): Step =
        StepBuilder("reactionDedupeStep", jobRepository)
            .tasklet(ReactionDedupeTasklet(useCase), transactionManager)
            .build()

    companion object {
        const val JOB_NAME = "reactionDedupeJob"
    }
}

/** 충돌이 소진될 때까지 회차를 반복한다. */
class ReactionDedupeTasklet(
    private val useCase: ReactionDedupeUseCase,
) : Tasklet {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        val executionContext = chunkContext.stepContext.stepExecution.executionContext
        val result = useCase.dedupe()

        val totalDeleted = executionContext.getInt(KEY_DELETED, 0) + result.deleted
        executionContext.putInt(KEY_DELETED, totalDeleted)
        contribution.incrementWriteCount(result.deleted.toLong())

        if (result.inspected == 0) {
            log.info("reaction dedupe finished: deleted={}", totalDeleted)
            return RepeatStatus.FINISHED
        }
        return RepeatStatus.CONTINUABLE
    }

    companion object {
        const val KEY_DELETED = "deleted"
    }
}
