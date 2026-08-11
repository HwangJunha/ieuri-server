package org.around.ieuri.tourism.infrastructure.batch

import org.around.ieuri.tourism.application.port.out.client.TourStatsSyncPort
import org.around.ieuri.tourism.application.port.out.persistence.TourStatsStorePort
import org.around.ieuri.tourism.application.port.out.persistence.SigunguCodeQueryPort
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
 * **방문자수만 지역을 나눠 부르지 않는다** — 이 API에만 지역 필터가 없어(spec §4.3) 날짜를 주면
 * 전 지역이 온다. 나머지 3종은 `areaCd`·`signguCd`가 필수라 252개 시군구를 순회한다.
 *
 * **호출량 주의**: 3종은 시군구마다 호출해 1회 실행이 252 호출이다(page-size 10,000 기준 —
 * 전 지역이 1페이지에 끝난다). page-size를 낮추면 그만큼 배가되어 개발 계정 일 한도 1,000건에
 * 걸린다. 스로틀링은 두지 않았다 — 한도 초과는 `UPSTREAM_RATE_LIMITED`로
 * 표면화되고 재실행이 중단 지점에서 이어진다.
 *
 * 조회 기준일은 잡 파라미터 `runDate`에서 파생하되 **업스트림 지연만큼 소급한다** —
 * 방문자수는 4일, 월 단위는 1개월(§4.6). 소급하지 않으면 매 실행이 빈 응답을 받는다.
 */
@Configuration
@Profile(BATCH_PROFILE)
class TourStatsSyncJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val syncPort: TourStatsSyncPort,
    private val storePort: TourStatsStorePort,
    private val sigunguCodePort: SigunguCodeQueryPort,
    private val properties: TourStatsProperties,
    private val syncProperties: TourStatsSyncProperties,
) {

    @Bean
    fun tourStatsVisitorCountSyncJob(): Job = singleStepJob(
        JOB_VISITOR_COUNT,
        TourStatsSyncTasklet(
            datasetName = "visitorCount",
            pageSize = properties.pageSize,
            sigunguCodePort = sigunguCodePort,
            // 지역 순회 없음 — 이 API만 지역 필터가 없어 region 인자를 쓰지 않는다.
            fetchPage = { runDate, _, pageNo ->
                // 최근 구간을 매번 통째로 다시 받는다 — upsert라 행이 늘지 않고(R7) 며칠 실패해도
                // 다음 성공 실행이 구멍을 메운다. 끝을 오늘로 두는 이유는 원천 지연(4일)만큼
                // 당길 필요가 없어서다 — 아직 없는 날짜는 행이 안 올 뿐이고, 지연이 짧아지면
                // 오히려 당겨 둔 만큼 놓친다.
                syncPort.fetchVisitorCounts(
                    runDate.minusDays(syncProperties.visitorBackfillDays - 1L),
                    runDate,
                    pageNo,
                    properties.pageSize,
                )
            },
            sigunguCodeOf = { it.sigunguCode },
            upsert = storePort::upsertVisitorCounts,
        ),
    )

    /**
     * 집중률은 날짜를 보내지 않는다 — 오퍼레이션이 날짜 파라미터를 받지 않으며, 시군구 하나를
     * 요청하면 향후 예측일이 전부 온다(실측 2026-08-11). 대상일은 행마다 다르다(R5).
     */
    @Bean
    fun tourStatsConcentrationRateSyncJob(): Job = singleStepJob(
        JOB_CONCENTRATION_RATE,
        TourStatsSyncTasklet(
            datasetName = "concentrationRate",
            pageSize = properties.pageSize,
            sigunguCodePort = sigunguCodePort,
            fetchPage = { _, region, pageNo ->
                syncPort.fetchConcentrationRates(
                    region.provinceCode,
                    region.code,
                    pageNo,
                    properties.pageSize,
                )
            },
            sigunguCodeOf = { it.sigunguCode },
            upsert = storePort::upsertConcentrationRates,
            regionsOf = SigunguCodeQueryPort::findAllRegions,
        ),
    )

    @Bean
    fun tourStatsStayIntensitySyncJob(): Job = singleStepJob(
        JOB_STAY_INTENSITY,
        TourStatsSyncTasklet(
            datasetName = "stayIntensity",
            pageSize = properties.pageSize,
            sigunguCodePort = sigunguCodePort,
            fetchPage = { runDate, region, pageNo ->
                syncPort.fetchStayIntensities(
                    region.provinceCode,
                    region.code,
                    baseMonthOf(runDate),
                    pageNo,
                    properties.pageSize,
                )
            },
            sigunguCodeOf = { it.sigunguCode },
            upsert = storePort::upsertStayIntensities,
            regionsOf = SigunguCodeQueryPort::findAllRegions,
        ),
    )

    @Bean
    fun tourStatsSpendingIntensitySyncJob(): Job = singleStepJob(
        JOB_SPENDING_INTENSITY,
        TourStatsSyncTasklet(
            datasetName = "spendingIntensity",
            pageSize = properties.pageSize,
            sigunguCodePort = sigunguCodePort,
            fetchPage = { runDate, region, pageNo ->
                syncPort.fetchSpendingIntensities(
                    region.provinceCode,
                    region.code,
                    baseMonthOf(runDate),
                    pageNo,
                    properties.pageSize,
                )
            },
            sigunguCodeOf = { it.sigunguCode },
            upsert = storePort::upsertSpendingIntensities,
            regionsOf = SigunguCodeQueryPort::findAllRegions,
        ),
    )

    private fun baseMonthOf(runDate: java.time.LocalDate): YearMonth =
        YearMonth.from(runDate).minusMonths(syncProperties.monthlyLagMonths)

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
