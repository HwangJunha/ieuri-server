package org.around.ieuri.tourism.adapter.persistence

import org.around.ieuri.common.persistence.stIntersects
import org.around.ieuri.tourism.adapter.persistence.table.DistrictTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotTable
import org.around.ieuri.tourism.application.shared.required.DistrictRepresentativeUpdatePort
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.RepresentativeLocation
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.core.wrapAsExpression
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Component

/** 시군구 대표 위치 저장(SPEC-0015 · V46). `ST_Intersects`라 경계선 위의 원본도 그 시군구에 든다(SPEC-0014). */
@Component
class DistrictRepresentativePersistenceAdapter : DistrictRepresentativeUpdatePort {

    override fun findKoreanSpotPoints(): Map<String, List<RepresentativeLocation.Point>> = DistrictTable
        .join(SpotTable, JoinType.INNER, additionalConstraint = { stIntersects(DistrictTable.boundary, SpotTable.location) })
        .select(DistrictTable.code, SpotTable.id, SpotTable.mapX, SpotTable.mapY)
        .where { SpotTable.language eq DomainLocale.KO.name }
        .groupBy({ it[DistrictTable.code] }) { RepresentativeLocation.Point(it[SpotTable.id], it[SpotTable.mapX], it[SpotTable.mapY]) }

    override fun saveRepresentative(districtCode: String, choice: RepresentativeLocation.Choice) {
        DistrictTable.update({ DistrictTable.code eq districtCode }) {
            it[DistrictTable.representativeLocation] = wrapAsExpression<String>(SpotTable.select(SpotTable.location).where { SpotTable.id eq choice.spotId })
            it[DistrictTable.representativeSpotCount] = choice.spotCount
            it[DistrictTable.representativeUpdatedAt] = CurrentTimestampWithTimeZone
        }
    }

    /** 이미 비어 있는 행은 건드리지 않는다 — 결과는 이번에 값을 잃은 시군구 수다. */
    override fun clearRepresentativesExcept(selectedCodes: Set<String>): Int = DistrictTable.update({
        DistrictTable.representativeLocation.isNotNull() and (DistrictTable.code notInList selectedCodes)
    }) {
        it[DistrictTable.representativeLocation] = null
        it[DistrictTable.representativeSpotCount] = null
        it[DistrictTable.representativeUpdatedAt] = CurrentTimestampWithTimeZone
    }
}
