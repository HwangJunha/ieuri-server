package org.around.ieuri.tourism.adapter.batch

import org.around.ieuri.tourism.adapter.config.KtoBigDataProperties
import org.around.ieuri.tourism.application.shared.provided.RegionMetricPageResult
import org.around.ieuri.tourism.application.shared.provided.RegionMetricSyncUseCase
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.time.YearMonth

/**
 * KTO 빅데이터 4종 동기화 잡 구성(SPEC-0015 §2.1). 갱신 주기(일/월)가 달라 잡을 4개로 나눈다.
 *
 * ```
 * tourStatsVisitorCountSyncJob        일 단위
 * tourStatsConcentrationRateSyncJob   일 단위 (향후 30일 예측)
 * tourStatsStayIntensitySyncJob       월 단위
 * tourStatsSpendingIntensitySyncJob   월 단위
 * ```
 *
 * - 실행 시점은 k8s CronJob이 소유하며 런처가 `ieuri.tourapi.sync.job`으로 하나를 고른다.
 * - 호출량: 방문자수 1, 강도 2종 각 17(+월 탐색분), 집중률 252. page-size를 낮추면 일 한도 1,000건에 걸린다.
 * - 스로틀링은 없다 — 한도 초과는 `UPSTREAM_RATE_LIMITED`로 표면화되고 재실행이 중단 지점에서 이어진다.
 */
@Configuration
@Profile(BATCH_PROFILE)
class RegionMetricSyncJobConfig(
    private val jobRepository: JobRepository,
    private val useCase: RegionMetricSyncUseCase,
    private val properties: KtoBigDataProperties,
    private val syncProperties: RegionMetricSyncProperties,
) {

    /** 최근 구간을 매번 통째로 다시 받는다 — upsert라 행이 늘지 않고(R7) 실패한 날은 다음 실행이 메운다. 지역 순회는 없다. */
    @Bean
    fun tourStatsVisitorCountSyncJob(): Job = singleStepJob(
        JOB_VISITOR_COUNT,
        RegionMetricSyncTasklet(
            datasetName = "visitorCount",
            pageSize = properties.pageSize,
            targets = { RegionMetricSyncTasklet.NO_REGION },
            syncPage = { context, _, pageNo ->
                useCase.syncVisitorCounts(context.runDate.minusDays(syncProperties.visitorBackfillDays - 1L), context.runDate, pageNo, properties.pageSize)
            },
        ),
    )

    /** 집중률. 날짜를 보내지 않으며 시군구 하나에 향후 예측일이 전부 온다(R5). 4종 중 유일하게 시군구 252개를 순회한다. */
    @Bean
    fun tourStatsConcentrationRateSyncJob(): Job = singleStepJob(
        JOB_CONCENTRATION_RATE,
        RegionMetricSyncTasklet(
            datasetName = "concentrationRate",
            pageSize = properties.pageSize,
            targets = useCase::districts,
            syncPage = { _, district, pageNo -> useCase.syncConcentrationRates(district, pageNo, properties.pageSize) },
        ),
    )

    @Bean
    fun tourStatsStayIntensitySyncJob(): Job = intensityJob(JOB_STAY_INTENSITY, "stayIntensity", useCase::syncStayIntensities)

    @Bean
    fun tourStatsSpendingIntensitySyncJob(): Job = intensityJob(JOB_SPENDING_INTENSITY, "spendingIntensity", useCase::syncSpendingIntensities)

    /** 강도 2종. 시도 17개만 순회한다. 기준월은 [RegionMetricMonthResolver]가 실행당 한 번 정한다. */
    private fun intensityJob(
        jobName: String,
        datasetName: String,
        sync: (String, YearMonth, Int, Int) -> RegionMetricPageResult,
    ): Job {
        val resolver = RegionMetricMonthResolver(
            datasetName = datasetName,
            lookbackMonths = syncProperties.monthlyLookbackMonths,
            sync = { areaCd, month, pageNo -> sync(areaCd, month, pageNo, properties.pageSize) },
        )
        return singleStepJob(
            jobName,
            RegionMetricSyncTasklet(
                datasetName = datasetName,
                pageSize = properties.pageSize,
                targets = useCase::provinces,
                syncPage = { context, areaCd, pageNo -> resolver.syncPage(context.executionContext, YearMonth.from(context.runDate), areaCd, pageNo) },
            ),
        )
    }

    /** 종마다 Step 하나뿐이다 — 지역 순회는 Step 안의 루프다. */
    private fun singleStepJob(name: String, tasklet: Tasklet): Job {
        val step: Step = StepBuilder("${name}Step", jobRepository)
            .tasklet(tasklet)
            .build()
        return JobBuilder(name, jobRepository).start(step).build()
    }

    companion object {
        const val JOB_VISITOR_COUNT = "tourStatsVisitorCountSyncJob"
        const val JOB_CONCENTRATION_RATE = "tourStatsConcentrationRateSyncJob"
        const val JOB_STAY_INTENSITY = "tourStatsStayIntensitySyncJob"
        const val JOB_SPENDING_INTENSITY = "tourStatsSpendingIntensitySyncJob"
    }
}
