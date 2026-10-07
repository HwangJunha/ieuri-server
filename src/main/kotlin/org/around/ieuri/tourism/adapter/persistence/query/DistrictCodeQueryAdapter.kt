package org.around.ieuri.tourism.adapter.persistence.query

import org.around.ieuri.tourism.adapter.persistence.mapper.toDistrictRegion
import org.around.ieuri.tourism.adapter.persistence.table.AreaTable
import org.around.ieuri.tourism.adapter.persistence.table.DistrictTable
import org.around.ieuri.tourism.application.region.required.DistrictCodeQueryPort
import org.around.ieuri.tourism.application.region.required.DistrictRegion
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.stereotype.Component

/**
 * 등록된 행정구역 코드 집합 조회(SPEC-0015 R9). 공간 컬럼은 읽지 않는다.
 * - 배치가 실행당 한 번 불러 전량을 메모리에서 대조한다.
 */
@Component
class DistrictCodeQueryAdapter : DistrictCodeQueryPort {
    override fun findAllCodes(): Set<String> = DistrictTable.select(DistrictTable.code).mapTo(mutableSetOf()) { it[DistrictTable.code] }

    /** 코드 오름차순 — 배치가 인덱스로 진행 위치를 기억하므로 순서가 안정적이어야 한다. */
    override fun findAllRegions(): List<DistrictRegion> = DistrictTable.select(DistrictTable.code, DistrictTable.areaCd)
        .orderBy(DistrictTable.code, SortOrder.ASC)
        .map { it.toDistrictRegion() }

    /** 시도 전체(17행, V74). 코드 오름차순이다. */
    override fun findAllProvinceCodes(): List<String> = AreaTable.select(AreaTable.areaCd)
        .orderBy(AreaTable.areaCd, SortOrder.ASC)
        .map { it[AreaTable.areaCd] }
}
