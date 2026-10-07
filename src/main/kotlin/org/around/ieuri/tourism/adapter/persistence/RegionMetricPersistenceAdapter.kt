package org.around.ieuri.tourism.adapter.persistence

import org.around.ieuri.common.persistence.setFromInsert
import org.around.ieuri.common.persistence.touchedWhenChanged
import org.around.ieuri.tourism.adapter.persistence.mapper.writeTo
import org.around.ieuri.tourism.adapter.persistence.table.KtoConcentrationRateTable
import org.around.ieuri.tourism.adapter.persistence.table.KtoDemandIntensityTable
import org.around.ieuri.tourism.adapter.persistence.table.KtoSpendingIntensityTable
import org.around.ieuri.tourism.adapter.persistence.table.KtoStayIntensityTable
import org.around.ieuri.tourism.adapter.persistence.table.KtoVisitorCountTable
import org.around.ieuri.tourism.application.shared.provided.dto.SyncConcentrationRateCommand
import org.around.ieuri.tourism.application.shared.provided.dto.SyncDemandIntensityCommand
import org.around.ieuri.tourism.application.shared.provided.dto.SyncVisitorCountCommand
import org.around.ieuri.tourism.application.shared.required.RegionMetricStorePort
import org.jetbrains.exposed.v1.jdbc.batchUpsert
import org.springframework.stereotype.Component

/**
 * KTO 빅데이터 4종 적재(SPEC-0015 R7). 자연키 upsert로 상류 정정을 덮어쓰며, 값이 바뀐 행만 `updated_at`을 올린다.
 * - 한 페이지를 한 번의 JDBC 배치로 보낸다. 모든 행이 쓰인다(삽입 또는 갱신).
 */
@Component
class RegionMetricPersistenceAdapter : RegionMetricStorePort {

    override fun upsertVisitorCounts(records: List<SyncVisitorCountCommand>): Int {
        KtoVisitorCountTable.batchUpsert(
            records,
            KtoVisitorCountTable.districtCode,
            KtoVisitorCountTable.baseDate,
            KtoVisitorCountTable.visitorType,
            shouldReturnGeneratedValues = false,
            onUpdate = {
                it.setFromInsert(KtoVisitorCountTable.touristCount)
                it[KtoVisitorCountTable.updatedAt] = touchedWhenChanged(KtoVisitorCountTable.updatedAt, listOf(KtoVisitorCountTable.touristCount))
            },
        ) { it.writeTo(this) }
        return records.size
    }

    override fun upsertConcentrationRates(records: List<SyncConcentrationRateCommand>): Int {
        KtoConcentrationRateTable.batchUpsert(
            records,
            KtoConcentrationRateTable.districtCode,
            KtoConcentrationRateTable.touristSpotName,
            KtoConcentrationRateTable.targetDate,
            shouldReturnGeneratedValues = false,
            onUpdate = {
                it.setFromInsert(KtoConcentrationRateTable.concentrationRate)
                it[KtoConcentrationRateTable.updatedAt] =
                    touchedWhenChanged(KtoConcentrationRateTable.updatedAt, listOf(KtoConcentrationRateTable.concentrationRate))
            },
        ) { it.writeTo(this) }
        return records.size
    }

    override fun upsertStayIntensities(records: List<SyncDemandIntensityCommand>): Int = upsertDemandIntensity(KtoStayIntensityTable, records)

    override fun upsertSpendingIntensities(records: List<SyncDemandIntensityCommand>): Int = upsertDemandIntensity(KtoSpendingIntensityTable, records)

    private fun upsertDemandIntensity(table: KtoDemandIntensityTable, records: List<SyncDemandIntensityCommand>): Int {
        table.batchUpsert(
            records,
            table.districtCode,
            table.baseMonth,
            table.indicatorCode,
            shouldReturnGeneratedValues = false,
            onUpdate = {
                it.setFromInsert(table.indicatorValue)
                it[table.updatedAt] = touchedWhenChanged(table.updatedAt, listOf(table.indicatorValue))
            },
        ) { it.writeTo(this, table) }
        return records.size
    }
}
