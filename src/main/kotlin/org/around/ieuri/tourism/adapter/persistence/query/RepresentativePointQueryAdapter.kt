package org.around.ieuri.tourism.adapter.persistence.query

import org.around.ieuri.common.persistence.asGeometry
import org.around.ieuri.common.persistence.stIntersects
import org.around.ieuri.common.persistence.stSnapToGrid
import org.around.ieuri.tourism.adapter.persistence.table.DistrictTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotTable
import org.around.ieuri.tourism.application.region.required.RepresentativePointQueryPort
import org.around.ieuri.tourism.domain.region.RepresentativeLocation
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.denseRank
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.stereotype.Component

/** 시군구 대표 위치 재료 조회(SPEC-0015 · V46). `ST_Intersects`라 경계선 위의 원본도 그 시군구에 든다(SPEC-0014). */
@Component
class RepresentativePointQueryAdapter : RepresentativePointQueryPort {

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
