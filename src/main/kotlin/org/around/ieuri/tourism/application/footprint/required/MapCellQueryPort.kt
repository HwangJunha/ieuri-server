package org.around.ieuri.tourism.application.footprint.required

import org.around.ieuri.tourism.application.footprint.provided.dto.CellSpotView
import org.around.ieuri.tourism.domain.footprint.MapCellDistrictRow
import org.around.ieuri.tourism.domain.shared.DomainLocale

/** 전국 지도 칸의 읽기 포트(SPEC-0030). */
interface MapCellQueryPort {

    /**
     * 칸을 이루는 시군구 전체. 칸 접기는 [org.around.ieuri.tourism.domain.shared.MapCellGrouping]이 한다.
     * - 페이지네이션하지 않는다(POL-017 예외).
     */
    fun findAllCellDistricts(): List<MapCellDistrictRow>

    /** 칸 1개를 이루는 시군구. 없는 칸이면 빈 목록이다. */
    fun findCellDistricts(cellId: Long): List<MapCellDistrictRow>

    /** 여러 칸을 이루는 시군구를 한 번에 읽는다. */
    fun findCellDistrictsIn(cellIds: Collection<Long>): List<MapCellDistrictRow>

    /**
     * 칸에 속한 장소(R11). 좋아요 수 내림차순, 같은 수는 id 오름차순이다.
     * - 페이지네이션이 없다 — 상위 [limit]건만 읽는다.
     */
    fun findCellSpots(
        cellId: Long,
        language: DomainLocale,
        limit: Int,
    ): List<CellSpotView>
}

/** 시군구 → 칸 대응 포트(SPEC-0030). 공간 컬럼을 읽지 않아 [MapCellQueryPort]와 나뉜다. */
interface MapCellDistrictQueryPort {

    /**
     * 시군구 코드 → 그 코드가 속한 칸 id.
     * - 중복이 제거되고 칸 id 오름차순이다. 없는 코드는 결과에서 빠진다.
     */
    fun findCellIdsByDistrictCodes(districtCodes: Collection<String>): List<Long>
}
