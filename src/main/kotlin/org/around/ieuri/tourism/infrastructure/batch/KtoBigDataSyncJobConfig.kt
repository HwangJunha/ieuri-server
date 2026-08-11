package org.around.ieuri.tourism.infrastructure.batch

import org.around.ieuri.tourism.application.port.out.client.KtoBigDataSyncPort
import org.around.ieuri.tourism.application.port.out.persistence.KtoBigDataStorePort
import org.around.ieuri.tourism.application.port.out.persistence.SigunguCodeQueryPort
import org.around.ieuri.tourism.infrastructure.config.KtoBigDataProperties
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
 * ktoVisitorCountSyncJob        일 단위
 * ktoConcentrationRateSyncJob   일 단위 (향후 30일 예측)
 * ktoStayIntensitySyncJob       월 단위
 * ktoSpendingIntensitySyncJob   월 단위
 * ```
 *
 * **잡을 4개로 나눈다** — 갱신 주기가 일/월로 갈려 하나로 묶으면 월 단위 종이 매일 헛돈다.
 * 실행 시점은 k8s CronJob이 소유하며 런처가 `ieuri.tourapi.sync.job`으로 하나를 고른다.
 *
 * **지역별로 나눠 호출하지 않는다** — 방문자수 API에 지역 필터가 없어(spec §4.3) 날짜를 주면
 * 전 지역이 한 번에 온다. 페이지네이션만 처리하면 된다.
 *
 * 조회 기준일은 잡 파라미터 `runDate`에서 파생하되 **업스트림 지연만큼 소급한다** —
 * 방문자수는 4일, 월 단위는 1개월(§4.6). 소급하지 않으면 매 실행이 빈 응답을 받는다.
 */
@Configuration
@Profile(BATCH_PROFILE)
class KtoBigDataSyncJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val syncPort: KtoBigDataSyncPort,
    private val storePort: KtoBigDataStorePort,
    private val sigunguCodePort: SigunguCodeQueryPort,
    private val properties: KtoBigDataProperties,
    private val syncProperties: KtoBigDataSyncProperties,
) {

    @Bean
    fun ktoVisitorCountSyncJob(): Job = singleStepJob(
        JOB_VISITOR_COUNT,
        KtoBigDataSyncTasklet(
            datasetName = "visitorCount",
            pageSize = properties.pageSize,
            sigunguCodePort = sigunguCodePort,
            fetchPage = { runDate, pageNo ->
                // 최근 구간을 매번 통째로 다시 받는다 — 자연키 upsert라 행이 늘지 않고(R7),
                // 배치가 며칠 실패해도 다음 성공 실행이 그 구멍을 메운다. 하루치만 받으면
                // 실패한 날의 데이터가 영구히 비는데, 아무도 모르는 종류의 결손이다.
                val endDate = runDate.minusDays(syncProperties.visitorLagDays)
                syncPort.fetchVisitorCounts(
                    endDate.minusDays(syncProperties.visitorBackfillDays - 1L),
                    endDate,
                    pageNo,
                    properties.pageSize,
                )
            },
            sigunguCodeOf = { it.sigunguCode },
            upsert = storePort::upsertVisitorCounts,
        ),
    )

    /** 집중률은 소급하지 않는다 — 발표 기준일 하나로 향후 30일 예측이 함께 온다(R5). */
    @Bean
    fun ktoConcentrationRateSyncJob(): Job = singleStepJob(
        JOB_CONCENTRATION_RATE,
        KtoBigDataSyncTasklet(
            datasetName = "concentrationRate",
            pageSize = properties.pageSize,
            sigunguCodePort = sigunguCodePort,
            fetchPage = { runDate, pageNo ->
                syncPort.fetchConcentrationRates(runDate, pageNo, properties.pageSize)
            },
            sigunguCodeOf = { it.sigunguCode },
            upsert = storePort::upsertConcentrationRates,
        ),
    )

    @Bean
    fun ktoStayIntensitySyncJob(): Job = singleStepJob(
        JOB_STAY_INTENSITY,
        KtoBigDataSyncTasklet(
            datasetName = "stayIntensity",
            pageSize = properties.pageSize,
            sigunguCodePort = sigunguCodePort,
            fetchPage = { runDate, pageNo ->
                syncPort.fetchStayIntensities(baseMonthOf(runDate), pageNo, properties.pageSize)
            },
            sigunguCodeOf = { it.sigunguCode },
            upsert = storePort::upsertStayIntensities,
        ),
    )

    @Bean
    fun ktoSpendingIntensitySyncJob(): Job = singleStepJob(
        JOB_SPENDING_INTENSITY,
        KtoBigDataSyncTasklet(
            datasetName = "spendingIntensity",
            pageSize = properties.pageSize,
            sigunguCodePort = sigunguCodePort,
            fetchPage = { runDate, pageNo ->
                syncPort.fetchSpendingIntensities(baseMonthOf(runDate), pageNo, properties.pageSize)
            },
            sigunguCodeOf = { it.sigunguCode },
            upsert = storePort::upsertSpendingIntensities,
        ),
    )

    private fun baseMonthOf(runDate: java.time.LocalDate): YearMonth =
        YearMonth.from(runDate).minusMonths(syncProperties.monthlyLagMonths)

    /** 종마다 Step 하나뿐이다 — 언어별로 갈리지 않고(전 지역 일괄 응답) 정리 단계도 없다. */
    private fun singleStepJob(name: String, tasklet: Tasklet): Job {
        val step: Step = StepBuilder("${name}Step", jobRepository)
            .tasklet(tasklet, transactionManager)
            .build()
        return JobBuilder(name, jobRepository).start(step).build()
    }

    companion object {
        const val JOB_VISITOR_COUNT = "ktoVisitorCountSyncJob"
        const val JOB_CONCENTRATION_RATE = "ktoConcentrationRateSyncJob"
        const val JOB_STAY_INTENSITY = "ktoStayIntensitySyncJob"
        const val JOB_SPENDING_INTENSITY = "ktoSpendingIntensitySyncJob"
    }
}
