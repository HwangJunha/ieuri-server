package org.around.ieuri.tourism.adapter.persistence

import org.around.ieuri.common.persistence.setFromInsert
import org.around.ieuri.common.persistence.touchedWhenChanged
import org.around.ieuri.tourism.adapter.persistence.mapper.writeSpendingIntensityTo
import org.around.ieuri.tourism.adapter.persistence.mapper.writeStayIntensityTo
import org.around.ieuri.tourism.adapter.persistence.mapper.writeTo
import org.around.ieuri.tourism.adapter.persistence.table.KtoConcentrationRateTable
import org.around.ieuri.tourism.adapter.persistence.table.KtoSpendingIntensityTable
import org.around.ieuri.tourism.adapter.persistence.table.KtoStayIntensityTable
import org.around.ieuri.tourism.adapter.persistence.table.KtoVisitorCountTable
import org.around.ieuri.tourism.application.region.provided.dto.SyncConcentrationRateCommand
import org.around.ieuri.tourism.application.region.provided.dto.SyncDemandIntensityCommand
import org.around.ieuri.tourism.application.region.provided.dto.SyncVisitorCountCommand
import org.around.ieuri.tourism.application.region.required.RegionMetricStorePort
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

    override fun upsertStayIntensities(records: List<SyncDemandIntensityCommand>): Int {
        KtoStayIntensityTable.batchUpsert(
            records,
            KtoStayIntensityTable.districtCode,
            KtoStayIntensityTable.baseMonth,
            KtoStayIntensityTable.indicatorCode,
            shouldReturnGeneratedValues = false,
            onUpdate = {
                it.setFromInsert(KtoStayIntensityTable.indicatorValue)
                it[KtoStayIntensityTable.updatedAt] = touchedWhenChanged(KtoStayIntensityTable.updatedAt, listOf(KtoStayIntensityTable.indicatorValue))
            },
        ) { it.writeStayIntensityTo(this) }
        return records.size
    }

    override fun upsertSpendingIntensities(records: List<SyncDemandIntensityCommand>): Int {
        KtoSpendingIntensityTable.batchUpsert(
            records,
            KtoSpendingIntensityTable.districtCode,
            KtoSpendingIntensityTable.baseMonth,
            KtoSpendingIntensityTable.indicatorCode,
            shouldReturnGeneratedValues = false,
            onUpdate = {
                it.setFromInsert(KtoSpendingIntensityTable.indicatorValue)
                it[KtoSpendingIntensityTable.updatedAt] =
                    touchedWhenChanged(KtoSpendingIntensityTable.updatedAt, listOf(KtoSpendingIntensityTable.indicatorValue))
            },
        ) { it.writeSpendingIntensityTo(this) }
        return records.size
    }
}
