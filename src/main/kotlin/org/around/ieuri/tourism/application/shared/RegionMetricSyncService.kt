package org.around.ieuri.tourism.application.shared

import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.tourism.application.shared.provided.RegionMetricPageResult
import org.around.ieuri.tourism.application.shared.provided.RegionMetricSyncUseCase
import org.around.ieuri.tourism.application.shared.required.DistrictCodeQueryPort
import org.around.ieuri.tourism.application.shared.required.DistrictRegion
import org.around.ieuri.tourism.application.shared.required.RegionMetricStorePort
import org.around.ieuri.tourism.application.shared.required.RegionMetricSyncPage
import org.around.ieuri.tourism.application.shared.required.RegionMetricSyncPort
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
) : RegionMetricSyncUseCase {

    override fun districts(): List<DistrictRegion> = districtCodePort.findAllRegions()

    override fun provinces(): List<String> = districtCodePort.findAllProvinceCodes()

    override fun syncVisitorCounts(startDate: LocalDate, endDate: LocalDate, pageNo: Int, pageSize: Int): RegionMetricPageResult = store(syncPort.fetchVisitorCounts(startDate, endDate, pageNo, pageSize), { it.districtCode }, storePort::upsertVisitorCounts)

    override fun syncConcentrationRates(district: DistrictRegion, pageNo: Int, pageSize: Int): RegionMetricPageResult = store(syncPort.fetchConcentrationRates(district.provinceCode, district.code, pageNo, pageSize), { it.districtCode }, storePort::upsertConcentrationRates)

    override fun syncStayIntensities(provinceCode: String, month: YearMonth, pageNo: Int, pageSize: Int): RegionMetricPageResult = store(syncPort.fetchStayIntensities(provinceCode, month, pageNo, pageSize), { it.districtCode }, storePort::upsertStayIntensities)

    override fun syncSpendingIntensities(provinceCode: String, month: YearMonth, pageNo: Int, pageSize: Int): RegionMetricPageResult = store(syncPort.fetchSpendingIntensities(provinceCode, month, pageNo, pageSize), { it.districtCode }, storePort::upsertSpendingIntensities)

    /** 등록된 시군구의 행만 적재한다(R9). 예외를 던지지 않는다. */
    private fun <T> store(page: RegionMetricSyncPage<T>, districtCodeOf: (T) -> String, upsert: (List<T>) -> Int): RegionMetricPageResult {
        val known = if (page.records.isEmpty()) emptySet() else districtCodePort.findAllCodes()
        val (accepted, skipped) = page.records.partition { districtCodeOf(it) in known }
        return RegionMetricPageResult(
            written = if (accepted.isEmpty()) 0 else upsert(accepted),
            skippedUnknownCode = skipped.size,
            dropped = page.droppedCount,
            totalCount = page.totalCount,
        )
    }
}
