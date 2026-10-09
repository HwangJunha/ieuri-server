package org.around.ieuri.tourism.adapter.persistence

import org.around.ieuri.tourism.adapter.persistence.mapper.writeSpendingIntensityTo
import org.around.ieuri.tourism.adapter.persistence.mapper.writeStayIntensityTo
import org.around.ieuri.tourism.adapter.persistence.mapper.writeTo
import org.around.ieuri.tourism.adapter.persistence.table.DistrictTable
import org.around.ieuri.tourism.adapter.persistence.table.KtoConcentrationRateTable
import org.around.ieuri.tourism.adapter.persistence.table.KtoSpendingIntensityTable
import org.around.ieuri.tourism.adapter.persistence.table.KtoStayIntensityTable
import org.around.ieuri.tourism.adapter.persistence.table.KtoVisitorCountTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotTable
import org.around.ieuri.tourism.application.region.required.DistrictRepository
import org.around.ieuri.tourism.domain.region.ConcentrationRate
import org.around.ieuri.tourism.domain.region.DemandIntensity
import org.around.ieuri.tourism.domain.region.District
import org.around.ieuri.tourism.domain.region.RepresentativeLocation
import org.around.ieuri.tourism.domain.region.VisitorCount
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isDistinctFrom
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.statements.insertValue
import org.jetbrains.exposed.v1.core.wrapAsExpression
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.batchUpsert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Component

/**
 * 시군구 애그리게이트 저장(SPEC-0015 · V46). 대표 위치와 KTO 빅데이터 4종(R7)을 쓴다.
 * - 지표는 자연키 upsert로 상류 정정을 덮어쓰며, 값이 바뀐 행만 `updated_at`을 올린다. 한 페이지를 한 번의 JDBC 배치로 보낸다.
 */
@Component
class DistrictPersistenceAdapter : DistrictRepository {

    override fun findAll(): List<District> = DistrictTable.select(DistrictTable.code, DistrictTable.representativeLocation)
        .map { District(code = it[DistrictTable.code], hasRepresentative = it[DistrictTable.representativeLocation] != null) }

    override fun saveRepresentative(districtCode: String, choice: RepresentativeLocation.Choice) {
        DistrictTable.update({ DistrictTable.code eq districtCode }) {
            it[DistrictTable.representativeLocation] = wrapAsExpression<String>(SpotTable.select(SpotTable.location).where { SpotTable.id eq choice.spotId })
            it[DistrictTable.representativeSpotCount] = choice.spotCount
            it[DistrictTable.representativeUpdatedAt] = CurrentTimestampWithTimeZone
        }
    }

    override fun clearRepresentative(districtCode: String) {
        DistrictTable.update({ (DistrictTable.code eq districtCode) and DistrictTable.representativeLocation.isNotNull() }) {
            it[DistrictTable.representativeLocation] = null
            it[DistrictTable.representativeSpotCount] = null
            it[DistrictTable.representativeUpdatedAt] = CurrentTimestampWithTimeZone
        }
    }

    override fun upsertVisitorCounts(counts: List<VisitorCount>): Int {
        KtoVisitorCountTable.batchUpsert(
            counts,
            KtoVisitorCountTable.districtCode,
            KtoVisitorCountTable.baseDate,
            KtoVisitorCountTable.visitorType,
            shouldReturnGeneratedValues = false,
            onUpdate = {
                it[KtoVisitorCountTable.touristCount] = insertValue(KtoVisitorCountTable.touristCount)
                it[KtoVisitorCountTable.updatedAt] = CurrentTimestampWithTimeZone
            },
            where = { KtoVisitorCountTable.touristCount isDistinctFrom insertValue(KtoVisitorCountTable.touristCount) },
        ) { it.writeTo(this) }
        return counts.size
    }

    override fun upsertConcentrationRates(rates: List<ConcentrationRate>): Int {
        KtoConcentrationRateTable.batchUpsert(
            rates,
            KtoConcentrationRateTable.districtCode,
            KtoConcentrationRateTable.touristSpotName,
            KtoConcentrationRateTable.targetDate,
            shouldReturnGeneratedValues = false,
            onUpdate = {
                it[KtoConcentrationRateTable.concentrationRate] = insertValue(KtoConcentrationRateTable.concentrationRate)
                it[KtoConcentrationRateTable.updatedAt] = CurrentTimestampWithTimeZone
            },
            where = { KtoConcentrationRateTable.concentrationRate isDistinctFrom insertValue(KtoConcentrationRateTable.concentrationRate) },
        ) { it.writeTo(this) }
        return rates.size
    }

    override fun upsertStayIntensities(intensities: List<DemandIntensity>): Int {
        KtoStayIntensityTable.batchUpsert(
            intensities,
            KtoStayIntensityTable.districtCode,
            KtoStayIntensityTable.baseMonth,
            KtoStayIntensityTable.indicatorCode,
            shouldReturnGeneratedValues = false,
            onUpdate = {
                it[KtoStayIntensityTable.indicatorValue] = insertValue(KtoStayIntensityTable.indicatorValue)
                it[KtoStayIntensityTable.updatedAt] = CurrentTimestampWithTimeZone
            },
            where = { KtoStayIntensityTable.indicatorValue isDistinctFrom insertValue(KtoStayIntensityTable.indicatorValue) },
        ) { it.writeStayIntensityTo(this) }
        return intensities.size
    }

    override fun upsertSpendingIntensities(intensities: List<DemandIntensity>): Int {
        KtoSpendingIntensityTable.batchUpsert(
            intensities,
            KtoSpendingIntensityTable.districtCode,
            KtoSpendingIntensityTable.baseMonth,
            KtoSpendingIntensityTable.indicatorCode,
            shouldReturnGeneratedValues = false,
            onUpdate = {
                it[KtoSpendingIntensityTable.indicatorValue] = insertValue(KtoSpendingIntensityTable.indicatorValue)
                it[KtoSpendingIntensityTable.updatedAt] = CurrentTimestampWithTimeZone
            },
            where = { KtoSpendingIntensityTable.indicatorValue isDistinctFrom insertValue(KtoSpendingIntensityTable.indicatorValue) },
        ) { it.writeSpendingIntensityTo(this) }
        return intensities.size
    }
}
