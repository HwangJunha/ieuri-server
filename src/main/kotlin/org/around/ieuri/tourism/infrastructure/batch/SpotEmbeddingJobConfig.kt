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
 * 장소 임베딩 적재 잡(SPEC-0035 S2). `spotSyncJob`과 **별개의 잡**이다 — 외부 제공자(Voyage)에 기대는 단계라
 * 그 실패·지연이 spot 동기화를 붙들지 않게 하고, 필요할 때 따로 돌릴 수 있게 한다.
 *
 * spot 동기화가 끝난 뒤에 돌아야 한다(번역 행이 대상 단위). CronJob은 완료 순서를 보장하지 않으므로
 * 매니페스트가 시각으로 벌려 둔다(`spotDistrictAssignmentJob`과 같은 방식).
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
