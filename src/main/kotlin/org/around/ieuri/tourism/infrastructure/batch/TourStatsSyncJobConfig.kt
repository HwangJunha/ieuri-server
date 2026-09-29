package org.around.ieuri.tourism.infrastructure.batch

import org.around.ieuri.tourism.application.command.SyncDemandIntensityCommand
import org.around.ieuri.tourism.application.port.out.client.TourStatsSyncPage
import org.around.ieuri.tourism.application.port.out.client.TourStatsSyncPort
import org.around.ieuri.tourism.application.port.out.persistence.DistrictCodeQueryPort
import org.around.ieuri.tourism.application.port.out.persistence.TourStatsStorePort
import org.around.ieuri.tourism.infrastructure.config.TourStatsProperties
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.transaction.PlatformTransactionManager
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
class TourStatsSyncJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val syncPort: TourStatsSyncPort,
    private val storePort: TourStatsStorePort,
    private val districtCodePort: DistrictCodeQueryPort,
    private val properties: TourStatsProperties,
    private val syncProperties: TourStatsSyncProperties,
) {

    @Bean
    fun tourStatsVisitorCountSyncJob(): Job = singleStepJob(
        JOB_VISITOR_COUNT,
        TourStatsSyncTasklet(
            datasetName = "visitorCount",
            pageSize = properties.pageSize,
            districtCodePort = districtCodePort,
            // 지역 순회 없음 — 이 API만 지역 필터가 없다.
            fetchPage = { context, _, pageNo ->
                // 최근 구간을 매번 통째로 다시 받는다 — upsert라 행이 늘지 않고(R7) 실패한 날은 다음 실행이 메운다.
                syncPort.fetchVisitorCounts(
                    context.runDate.minusDays(syncProperties.visitorBackfillDays - 1L),
                    context.runDate,
                    pageNo,
                    properties.pageSize,
                )
            },
            districtCodeOf = { it.districtCode },
            upsert = storePort::upsertVisitorCounts,
            targetsOf = { TourStatsSyncTasklet.NO_REGION },
        ),
    )

    /**
     * 집중률. 날짜를 보내지 않으며 시군구 하나에 향후 예측일이 전부 온다(R5).
     * - 4종 중 유일하게 시군구 252개를 순회한다 — `signguCd`가 필수다.
     */
    @Bean
    fun tourStatsConcentrationRateSyncJob(): Job = singleStepJob(
        JOB_CONCENTRATION_RATE,
        TourStatsSyncTasklet(
            datasetName = "concentrationRate",
            pageSize = properties.pageSize,
            districtCodePort = districtCodePort,
            fetchPage = { _, region, pageNo ->
                syncPort.fetchConcentrationRates(
                    region.provinceCode,
                    region.code,
                    pageNo,
                    properties.pageSize,
                )
            },
            districtCodeOf = { it.districtCode },
            upsert = storePort::upsertConcentrationRates,
            targetsOf = DistrictCodeQueryPort::findAllRegions,
        ),
    )

    @Bean
    fun tourStatsStayIntensitySyncJob(): Job = intensityJob(JOB_STAY_INTENSITY, "stayIntensity", storePort::upsertStayIntensities, syncPort::fetchStayIntensities)

    @Bean
    fun tourStatsSpendingIntensitySyncJob(): Job = intensityJob(
        JOB_SPENDING_INTENSITY,
        "spendingIntensity",
        storePort::upsertSpendingIntensities,
        syncPort::fetchSpendingIntensities,
    )

    /**
     * 강도 2종. 시도 17개만 순회한다 — `areaCd`만 주면 그 시도의 시군구 전체가 온다.
     * - 기준월은 [TourStatsMonthResolver]가 실행당 한 번 정한다.
     */
    private fun intensityJob(
        jobName: String,
        datasetName: String,
        upsert: (List<SyncDemandIntensityCommand>) -> Int,
        fetch: (String, YearMonth, Int, Int) -> TourStatsSyncPage<SyncDemandIntensityCommand>,
    ): Job {
        val resolver = TourStatsMonthResolver(
            datasetName = datasetName,
            lookbackMonths = syncProperties.monthlyLookbackMonths,
            fetch = { areaCd, month, pageNo -> fetch(areaCd, month, pageNo, properties.pageSize) },
        )
        return singleStepJob(
            jobName,
            TourStatsSyncTasklet(
                datasetName = datasetName,
                pageSize = properties.pageSize,
                districtCodePort = districtCodePort,
                fetchPage = { context, areaCd, pageNo ->
                    resolver.fetchPage(
                        context.executionContext,
                        YearMonth.from(context.runDate),
                        areaCd,
                        pageNo,
                    )
                },
                districtCodeOf = { it.districtCode },
                upsert = upsert,
                targetsOf = DistrictCodeQueryPort::findAllProvinceCodes,
            ),
        )
    }

    /** 종마다 Step 하나뿐이다 — 지역 순회는 Step 안의 루프다. */
    private fun singleStepJob(name: String, tasklet: Tasklet): Job {
        val step: Step = StepBuilder("${name}Step", jobRepository)
            .tasklet(tasklet, transactionManager)
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
