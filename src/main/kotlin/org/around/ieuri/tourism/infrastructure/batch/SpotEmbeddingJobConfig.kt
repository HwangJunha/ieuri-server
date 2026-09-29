package org.around.ieuri.tourism.infrastructure.batch

import org.around.ieuri.tourism.application.port.`in`.command.SpotEmbeddingSyncUseCase
import org.around.ieuri.tourism.infrastructure.config.RecommendationProperties
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.transaction.PlatformTransactionManager

/**
 * 장소 임베딩 적재 잡(SPEC-0035). 외부 제공자(Voyage) 실패가 spot 동기화를 붙들지 않게 별개 잡이다.
 * - spot 동기화 뒤에 돌아야 한다 — CronJob 시각으로 벌려 둔다.
 */
@Configuration
@Profile(BATCH_PROFILE)
class SpotEmbeddingJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val embeddingSyncUseCase: SpotEmbeddingSyncUseCase,
    private val properties: RecommendationProperties,
) {

    @Bean
    fun spotEmbeddingJob(): Job {
        val step: Step = StepBuilder("${JOB_NAME}Step", jobRepository)
            .tasklet(SpotEmbeddingTasklet(embeddingSyncUseCase, properties.embedding.batchSize), transactionManager)
            .build()
        return JobBuilder(JOB_NAME, jobRepository).start(step).build()
    }

    companion object {
        const val JOB_NAME = "spotEmbeddingJob"
    }
}
