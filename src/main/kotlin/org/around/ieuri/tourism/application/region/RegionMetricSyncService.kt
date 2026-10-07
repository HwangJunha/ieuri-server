package org.around.ieuri.tourism.application.region

import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.tourism.application.region.provided.RegionMetricDataset
import org.around.ieuri.tourism.application.region.provided.RegionMetricPageResult
import org.around.ieuri.tourism.application.region.provided.RegionMetricSyncUseCase
import org.around.ieuri.tourism.application.region.provided.RegionMetricTarget
import org.around.ieuri.tourism.application.region.provided.dto.SyncDemandIntensityCommand
import org.around.ieuri.tourism.application.region.required.DistrictCodeQueryPort
import org.around.ieuri.tourism.application.region.required.RegionMetricStorePort
import org.around.ieuri.tourism.application.region.required.RegionMetricSyncPage
import org.around.ieuri.tourism.application.region.required.RegionMetricSyncPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.YearMonth

/** KTO 빅데이터 동기화. 상류 호출은 첫 쿼리 전에 한다 — 연결을 잡은 채 기다리지 않는다. */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX)
class RegionMetricSyncService(
    private val syncPort: RegionMetricSyncPort,
    private val storePort: RegionMetricStorePort,
    private val districtCodePort: DistrictCodeQueryPort,
    private val policy: RegionMetricSyncPolicy,
) : RegionMetricSyncUseCase {

    override fun targets(dataset: RegionMetricDataset): List<RegionMetricTarget> = when (dataset) {
        RegionMetricDataset.VISITOR_COUNT -> listOf(RegionMetricTarget.NATIONWIDE)

        RegionMetricDataset.CONCENTRATION_RATE -> districtCodePort.findAllRegions().map { RegionMetricTarget(it.provinceCode, it.code) }

        RegionMetricDataset.STAY_INTENSITY, RegionMetricDataset.SPENDING_INTENSITY ->
            districtCodePort.findAllProvinceCodes().map { RegionMetricTarget(provinceCode = it, districtCode = null) }
    }

    override fun syncPage(dataset: RegionMetricDataset, target: RegionMetricTarget, pageNo: Int, runDate: LocalDate, baseMonth: YearMonth?): RegionMetricPageResult = when (dataset) {
        RegionMetricDataset.VISITOR_COUNT -> {
            val page = syncPort.fetchVisitorCounts(runDate.minusDays(policy.visitorBackfillDays - 1), runDate, pageNo, policy.pageSize)
            store(page, pageNo, { it.districtCode }, storePort::upsertVisitorCounts)
        }

        RegionMetricDataset.CONCENTRATION_RATE -> {
            val page = syncPort.fetchConcentrationRates(requireNotNull(target.provinceCode), requireNotNull(target.districtCode), pageNo, policy.pageSize)
            store(page, pageNo, { it.districtCode }, storePort::upsertConcentrationRates)
        }

        RegionMetricDataset.STAY_INTENSITY ->
            syncIntensity(requireNotNull(target.provinceCode), pageNo, runDate, baseMonth, syncPort::fetchStayIntensities, storePort::upsertStayIntensities)

        RegionMetricDataset.SPENDING_INTENSITY ->
            syncIntensity(requireNotNull(target.provinceCode), pageNo, runDate, baseMonth, syncPort::fetchSpendingIntensities, storePort::upsertSpendingIntensities)
    }

    /** 기준월이 정해졌으면 그 달을, 아니면 이번 달부터 거슬러 올라가 자료가 있는 첫 달의 응답을 그대로 쓴다. 창 안에 없으면 0건이다(R8). */
    private fun syncIntensity(
        provinceCode: String,
        pageNo: Int,
        runDate: LocalDate,
        baseMonth: YearMonth?,
        fetch: (String, YearMonth, Int, Int) -> RegionMetricSyncPage<SyncDemandIntensityCommand>,
        upsert: (List<SyncDemandIntensityCommand>) -> Int,
    ): RegionMetricPageResult {
        val candidates = baseMonth?.let { listOf(it) } ?: (0 until policy.monthlyLookbackMonths).map { YearMonth.from(runDate).minusMonths(it.toLong()) }
        candidates.forEach { month ->
            val page = fetch(provinceCode, month, pageNo, policy.pageSize)
            if (page.totalCount > 0 || baseMonth != null) return store(page, pageNo, { it.districtCode }, upsert).copy(baseMonth = month)
        }
        return RegionMetricPageResult(written = 0, skippedUnknownCode = 0, dropped = 0, hasMore = false)
    }

    /** 등록된 시군구의 행만 적재한다(R9). 예외를 던지지 않는다. */
    private fun <T> store(page: RegionMetricSyncPage<T>, pageNo: Int, districtCodeOf: (T) -> String, upsert: (List<T>) -> Int): RegionMetricPageResult {
        val knownCodes = if (page.records.isEmpty()) emptySet() else districtCodePort.findAllCodes()
        val (accepted, skipped) = page.records.partition { districtCodeOf(it) in knownCodes }
        return RegionMetricPageResult(
            written = if (accepted.isEmpty()) 0 else upsert(accepted),
            skippedUnknownCode = skipped.size,
            dropped = page.droppedCount,
            hasMore = page.hasMore(pageNo, policy.pageSize),
        )
    }
}
