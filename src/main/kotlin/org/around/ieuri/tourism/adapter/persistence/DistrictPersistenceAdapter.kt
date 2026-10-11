package org.around.ieuri.tourism.adapter.persistence

import org.around.ieuri.common.persistence.asGeometry
import org.around.ieuri.common.persistence.stIntersects
import org.around.ieuri.common.persistence.stSnapToGrid
import org.around.ieuri.tourism.adapter.persistence.mapper.toDistrict
import org.around.ieuri.tourism.adapter.persistence.mapper.toDistrictRegion
import org.around.ieuri.tourism.adapter.persistence.mapper.writeSpendingIntensityTo
import org.around.ieuri.tourism.adapter.persistence.mapper.writeStayIntensityTo
import org.around.ieuri.tourism.adapter.persistence.mapper.writeTo
import org.around.ieuri.tourism.adapter.persistence.table.AreaTable
import org.around.ieuri.tourism.adapter.persistence.table.DistrictTable
import org.around.ieuri.tourism.adapter.persistence.table.KtoConcentrationRateTable
import org.around.ieuri.tourism.adapter.persistence.table.KtoSpendingIntensityTable
import org.around.ieuri.tourism.adapter.persistence.table.KtoStayIntensityTable
import org.around.ieuri.tourism.adapter.persistence.table.KtoVisitorCountTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotTable
import org.around.ieuri.tourism.application.region.required.DistrictRepository
import org.around.ieuri.tourism.application.region.required.dto.DistrictRegion
import org.around.ieuri.tourism.domain.region.ConcentrationRate
import org.around.ieuri.tourism.domain.region.DemandIntensity
import org.around.ieuri.tourism.domain.region.District
import org.around.ieuri.tourism.domain.region.RepresentativeLocation
import org.around.ieuri.tourism.domain.region.VisitorCount
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.denseRank
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

    override fun findAll(): List<District> = DistrictTable.select(DistrictTable.code, DistrictTable.representativeLocation).map { it.toDistrict() }

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

    /** 코드 오름차순 — 배치가 인덱스로 진행 위치를 기억하므로 순서가 안정적이어야 한다. */
    override fun findAllRegions(): List<DistrictRegion> = DistrictTable.select(DistrictTable.code, DistrictTable.areaCd)
        .orderBy(DistrictTable.code, SortOrder.ASC)
        .map { it.toDistrictRegion() }

    /** 시도 전체(17행, V74). 코드 오름차순이다. */
    override fun findAllProvinceCodes(): List<String> = AreaTable.select(AreaTable.areaCd)
        .orderBy(AreaTable.areaCd, SortOrder.ASC)
        .map { it[AreaTable.areaCd] }

    /** 격자 나누기와 격자 순서는 PostgreSQL이 정한다 — 동률 격자를 고르는 순서(geometry 정렬)를 코드로 옮기면 dev와 결과가 달라진다(SPEC-0039 D-113). */
    override fun findKoreanSpotPoints(cellSizeDegrees: Double): Map<String, List<RepresentativeLocation.Point>> {
        val cellRank = denseRank().over().partitionBy(DistrictTable.code).orderBy(stSnapToGrid(SpotTable.location.asGeometry(), cellSizeDegrees))
        return DistrictTable
            .join(SpotTable, JoinType.INNER, additionalConstraint = { stIntersects(DistrictTable.boundary, SpotTable.location) })
            .select(DistrictTable.code, SpotTable.id, SpotTable.mapX, SpotTable.mapY, cellRank)
            .where { SpotTable.language eq DomainLocale.KO.name }
            .groupBy({ it[DistrictTable.code] }) { RepresentativeLocation.Point(it[SpotTable.id], it[SpotTable.mapX], it[SpotTable.mapY], it[cellRank]) }
    }
}
