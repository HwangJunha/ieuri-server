package org.around.ieuri.tourism.application.region.required

import org.around.ieuri.tourism.domain.region.RepresentativeLocation

/** 시군구 대표 위치 저장(SPEC-0015). 고르는 규칙은 도메인([RepresentativeLocation])에 있다. */
interface DistrictRepresentativeUpdatePort {
    /** 시군구별 KO 원본 좌표와 그 원본이 든 격자의 순위(`ST_SnapToGrid(location, [cellSizeDegrees])`를 geometry 순서로 매긴 dense rank). */
    fun findKoreanSpotPoints(cellSizeDegrees: Double): Map<String, List<RepresentativeLocation.Point>>

    /** 대표 위치를 그 원본의 위치로 쓴다. */
    fun saveRepresentative(districtCode: String, choice: RepresentativeLocation.Choice)

    /** [selectedCodes]에 없는 시군구의 대표 위치를 비우고, 이번에 값을 잃은 수를 돌려준다. */
    fun clearRepresentativesExcept(selectedCodes: Set<String>): Int
}
