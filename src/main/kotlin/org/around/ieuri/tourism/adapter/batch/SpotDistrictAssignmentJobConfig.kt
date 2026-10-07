package org.around.ieuri.tourism.adapter.batch

import org.around.ieuri.tourism.application.catalog.provided.SpotCatalogCurationUseCase
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * 카탈로그 시군구 귀속 잡 구성(SPEC-0030 R11 · V75). 단일 Step이다.
 * - `spotSyncJob` 이후에 실행해야 한다 — 순서는 CronJob 스케줄이 보장한다(spot 04:00 → 이 잡 06:00).
 *
 * 실행: `--ieuri.tourapi.sync.job=spotDistrictAssignmentJob`
 */
@Configuration
@Profile(BATCH_PROFILE)
class SpotDistrictAssignmentJobConfig(
    private val jobRepository: JobRepository,
    private val curationUseCase: SpotCatalogCurationUseCase,
) {

    @Bean
    fun spotDistrictAssignmentJob(): Job {
        val step: Step = StepBuilder("${JOB_NAME}Step", jobRepository)
            .tasklet(SpotDistrictAssignmentTasklet(curationUseCase))
            .build()
        return JobBuilder(JOB_NAME, jobRepository).start(step).build()
    }

    companion object {
        const val JOB_NAME = "spotDistrictAssignmentJob"
    }
}
