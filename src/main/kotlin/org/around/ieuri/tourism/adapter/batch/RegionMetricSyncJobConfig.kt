package org.around.ieuri.tourism.adapter.batch

import org.around.ieuri.tourism.application.region.provided.RegionMetricSyncUseCase
import org.around.ieuri.tourism.application.region.provided.dto.RegionMetricDataset
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * KTO 빅데이터 4종 동기화 잡(SPEC-0015 §2.1). 갱신 주기(일/월)가 달라 잡이 4개다. 실행 시점은 k8s CronJob이 소유한다.
 * - 잡 이름(`tourStats*`)은 CronJob이 참조해 그대로 둔다.
 */
@Configuration
@Profile(BATCH_PROFILE)
class RegionMetricSyncJobConfig(
    private val jobRepository: JobRepository,
    private val useCase: RegionMetricSyncUseCase,
) {

    @Bean
    fun tourStatsVisitorCountSyncJob(): Job = job(JOB_VISITOR_COUNT, RegionMetricDataset.VISITOR_COUNT)

    @Bean
    fun tourStatsConcentrationRateSyncJob(): Job = job(JOB_CONCENTRATION_RATE, RegionMetricDataset.CONCENTRATION_RATE)

    @Bean
    fun tourStatsStayIntensitySyncJob(): Job = job(JOB_STAY_INTENSITY, RegionMetricDataset.STAY_INTENSITY)

    @Bean
    fun tourStatsSpendingIntensitySyncJob(): Job = job(JOB_SPENDING_INTENSITY, RegionMetricDataset.SPENDING_INTENSITY)

    /** 종마다 Step 하나다 — 지역 순회는 Step 안의 반복이다. */
    private fun job(name: String, dataset: RegionMetricDataset): Job = JobBuilder(name, jobRepository)
        .start(StepBuilder("${name}Step", jobRepository).tasklet(RegionMetricSyncTasklet(dataset, useCase)).build())
        .build()

    companion object {
        const val JOB_VISITOR_COUNT = "tourStatsVisitorCountSyncJob"
        const val JOB_CONCENTRATION_RATE = "tourStatsConcentrationRateSyncJob"
        const val JOB_STAY_INTENSITY = "tourStatsStayIntensitySyncJob"
        const val JOB_SPENDING_INTENSITY = "tourStatsSpendingIntensitySyncJob"
    }
}
