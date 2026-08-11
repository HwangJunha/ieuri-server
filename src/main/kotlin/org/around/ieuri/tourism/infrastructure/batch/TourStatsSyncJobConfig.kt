package org.around.ieuri.tourism.infrastructure.batch

import org.around.ieuri.tourism.application.command.SyncDemandIntensityCommand
import org.around.ieuri.tourism.application.port.out.client.TourStatsSyncPage
import org.around.ieuri.tourism.application.port.out.client.TourStatsSyncPort
import org.around.ieuri.tourism.application.port.out.persistence.TourStatsStorePort
import org.around.ieuri.tourism.application.port.out.persistence.DistrictCodeQueryPort
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
 * KTO 빅데이터 4종 동기화 잡 구성(SPEC-0015 §2.1).
 *
 * ```
 * tourStatsVisitorCountSyncJob        일 단위
 * tourStatsConcentrationRateSyncJob   일 단위 (향후 30일 예측)
 * tourStatsStayIntensitySyncJob       월 단위
 * tourStatsSpendingIntensitySyncJob   월 단위
 * ```
 *
 * **잡을 4개로 나눈다** — 갱신 주기가 일/월로 갈려 하나로 묶으면 월 단위 종이 매일 헛돈다.
 * 실행 시점은 k8s CronJob이 소유하며 런처가 `ieuri.tourapi.sync.job`으로 하나를 고른다.
 *
 * **순회 단위가 종마다 다르다**(2026-08-11 실측). 방문자수는 지역 필터가 없어(spec §4.3) 날짜만
 * 주면 전 지역이 오고, 강도 2종은 `areaCd`만으로 그 시도의 시군구 전체가 와서 시도 17개만
 * 돌며, 집중률만 `signguCd`가 필수라 시군구 252개를 돈다.
 *
 * **호출량**: 방문자수 1, 강도 2종 각 17(+월 탐색분), 집중률 252 — page-size 10,000 기준으로
 * 지역별 1페이지다. page-size를 낮추면 그만큼 배가되어 개발 계정 일 한도 1,000건에 걸린다.
 * 스로틀링은 두지 않았다 — 한도 초과는 `UPSTREAM_RATE_LIMITED`로 표면화되고 재실행이 중단
 * 지점에서 이어진다.
 *
 * 방문자수는 `runDate`에서 소급 구간을 잡고, 월 지표는 **고정 소급이 아니라 탐색으로** 기준월을
 * 정한다([TourStatsMonthResolver]) — 가용한 최신 월이 고정되어 있지 않기 때문이다.
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
            // 지역 순회 없음 — 이 API만 지역 필터가 없어 순회 단위를 쓰지 않는다.
            fetchPage = { context, _, pageNo ->
                // 최근 구간을 매번 통째로 다시 받는다 — upsert라 행이 늘지 않고(R7) 며칠 실패해도
                // 다음 성공 실행이 구멍을 메운다. 끝을 오늘로 두는 이유는 원천 지연(4일)만큼
                // 당길 필요가 없어서다 — 아직 없는 날짜는 행이 안 올 뿐이고, 지연이 짧아지면
                // 오히려 당겨 둔 만큼 놓친다.
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
     * 집중률은 날짜를 보내지 않는다 — 오퍼레이션이 날짜 파라미터를 받지 않으며, 시군구 하나를
     * 요청하면 향후 예측일이 전부 온다(실측 2026-08-11). 대상일은 행마다 다르다(R5).
     *
     * **4종 중 유일하게 시군구 252개를 순회한다** — 이 오퍼레이션만 `signguCd`가 필수라
     * `areaCd`만 보내면 `NO_MANDATORY_REQUEST_PARAMETERS_ERROR1(signguCd)`이 온다.
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
    fun tourStatsStayIntensitySyncJob(): Job =
        intensityJob(JOB_STAY_INTENSITY, "stayIntensity", storePort::upsertStayIntensities, syncPort::fetchStayIntensities)

    @Bean
    fun tourStatsSpendingIntensitySyncJob(): Job =
        intensityJob(
            JOB_SPENDING_INTENSITY,
            "spendingIntensity",
            storePort::upsertSpendingIntensities,
            syncPort::fetchSpendingIntensities,
        )

    /**
     * 강도 2종은 순회 단위·월 탐색·필터가 전부 같고 오퍼레이션과 적재 대상만 다르다.
     *
     * **시도 17개만 순회한다** — `areaCd`만 주면 그 시도의 시군구 전체가 한 응답에 온다
     * (실측 2026-08-11: 서울 26행). 252 순회 대비 호출이 1/15로 줄어든다.
     *
     * 기준월은 [TourStatsMonthResolver]가 **실행당 한 번** 정한다 — 시도마다 탐색하면 호출이
     * 시도 수만큼 곱해진다.
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

    /** 종마다 Step 하나뿐이다 — 지역 순회는 Step 안의 루프이며 언어 분기도 정리 단계도 없다. */
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
