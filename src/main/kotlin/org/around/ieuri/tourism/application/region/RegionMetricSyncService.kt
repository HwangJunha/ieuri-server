package org.around.ieuri.tourism.application.region

import org.around.ieuri.common.pagination.OffsetPage
import org.around.ieuri.tourism.application.region.provided.RegionMetricSyncUseCase
import org.around.ieuri.tourism.application.region.provided.dto.RegionMetricDataset
import org.around.ieuri.tourism.application.region.provided.dto.RegionMetricPageResult
import org.around.ieuri.tourism.application.region.provided.dto.RegionMetricTarget
import org.around.ieuri.tourism.application.region.required.DistrictRepository
import org.around.ieuri.tourism.application.region.required.RegionMetricSyncPort
import org.around.ieuri.tourism.application.region.required.dto.DemandIntensityRecord
import org.around.ieuri.tourism.application.region.required.dto.RegionMetricSyncPage
import org.around.ieuri.tourism.domain.region.DemandIntensity
import org.around.ieuri.tourism.domain.region.District
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.YearMonth

/** KTO 빅데이터 동기화. 상류 호출은 첫 쿼리 전에 한다 — 연결을 잡은 채 기다리지 않는다. */
@Service
@Transactional
class RegionMetricSyncService(
    private val syncPort: RegionMetricSyncPort,
    private val districtRepository: DistrictRepository,
    private val policy: RegionMetricSyncPolicy,
) : RegionMetricSyncUseCase {

    override fun targets(dataset: RegionMetricDataset): List<RegionMetricTarget> = when (dataset) {
        RegionMetricDataset.VISITOR_COUNT -> listOf(RegionMetricTarget.NATIONWIDE)

        RegionMetricDataset.CONCENTRATION_RATE -> districtRepository.findAllRegions().map { RegionMetricTarget(it.provinceCode, it.code) }

        RegionMetricDataset.STAY_INTENSITY, RegionMetricDataset.SPENDING_INTENSITY ->
            districtRepository.findAllProvinceCodes().map { RegionMetricTarget(provinceCode = it, districtCode = null) }
    }

    override fun syncPage(dataset: RegionMetricDataset, target: RegionMetricTarget, pageNo: Int, runDate: LocalDate, baseMonth: YearMonth?): RegionMetricPageResult = when (dataset) {
        RegionMetricDataset.VISITOR_COUNT -> {
            val page = syncPort.fetchVisitorCounts(runDate.minusDays(policy.visitorBackfillDays - 1), runDate, pageNo, policy.pageSize)
            store(page, pageNo, { it.districtCode }, { district, r -> district.visitorCount(r.baseDate, r.visitorType, r.touristCount) }, districtRepository::upsertVisitorCounts)
        }

        RegionMetricDataset.CONCENTRATION_RATE -> {
            val page = syncPort.fetchConcentrationRates(requireNotNull(target.provinceCode), requireNotNull(target.districtCode), pageNo, policy.pageSize)
            store(page, pageNo, { it.districtCode }, { district, r -> district.concentrationRate(r.touristSpotName, r.targetDate, r.concentrationRate) }, districtRepository::upsertConcentrationRates)
        }

        RegionMetricDataset.STAY_INTENSITY ->
            syncIntensity(requireNotNull(target.provinceCode), pageNo, runDate, baseMonth, syncPort::fetchStayIntensities, districtRepository::upsertStayIntensities)

        RegionMetricDataset.SPENDING_INTENSITY ->
            syncIntensity(requireNotNull(target.provinceCode), pageNo, runDate, baseMonth, syncPort::fetchSpendingIntensities, districtRepository::upsertSpendingIntensities)
    }

    /** 기준월이 정해졌으면 그 달을, 아니면 이번 달부터 거슬러 올라가 자료가 있는 첫 달의 응답을 그대로 쓴다. 창 안에 없으면 0건이다(R8). */
    private fun syncIntensity(
        provinceCode: String,
        pageNo: Int,
        runDate: LocalDate,
        baseMonth: YearMonth?,
        fetch: (String, YearMonth, Int, Int) -> RegionMetricSyncPage<DemandIntensityRecord>,
        upsert: (List<DemandIntensity>) -> Int,
    ): RegionMetricPageResult {
        val candidates = baseMonth?.let { listOf(it) } ?: (0 until policy.monthlyLookbackMonths).map { YearMonth.from(runDate).minusMonths(it.toLong()) }
        candidates.forEach { month ->
            val page = fetch(provinceCode, month, pageNo, policy.pageSize)
            if (page.totalCount > 0 || baseMonth != null) return store(page, pageNo, { it.districtCode }, { district, r -> district.demandIntensity(r.baseMonth, r.indicatorCode, r.indicatorValue) }, upsert).copy(baseMonth = month)
        }
        return RegionMetricPageResult(written = 0, skippedUnknownCode = 0, dropped = 0, hasMore = false)
    }

    /** 등록된 시군구([District])가 만든 지표만 적재한다 — 모르는 코드는 건너뛴다(R9). 예외를 던지지 않는다. */
    private fun <R, M> store(
        page: RegionMetricSyncPage<R>,
        pageNo: Int,
        districtCodeOf: (R) -> String,
        metricOf: (District, R) -> M,
        upsert: (List<M>) -> Int,
    ): RegionMetricPageResult {
        val districts = if (page.records.isEmpty()) emptyMap() else districtRepository.findAll().associateBy { it.code }
        val metrics = page.records.mapNotNull { record -> districts[districtCodeOf(record)]?.let { metricOf(it, record) } }
        return RegionMetricPageResult(
            written = if (metrics.isEmpty()) 0 else upsert(metrics),
            skippedUnknownCode = page.records.size - metrics.size,
            dropped = page.droppedCount,
            hasMore = OffsetPage.hasNextPage(pageNo, policy.pageSize, page.totalCount),
        )
    }
}
