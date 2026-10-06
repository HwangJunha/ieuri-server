package org.around.ieuri.tourism.application.shared.required

/**
 * 시군구 대표 위치 갱신 포트(SPEC-0015). 전 시군구를 DB 안에서 한 번에 갱신한다.
 * - 격자 크기는 도 단위이며, [minSpotCount] 미달 시군구는 NULL로 되돌린다.
 */
interface DistrictRepresentativeUpdatePort {

    fun refreshRepresentativeLocations(cellSizeDegrees: Double, minSpotCount: Int): DistrictRepresentativeRefreshResult
}

/** 대표 위치 갱신 결과. [cleared]는 이전에 값이 있다가 NULL로 되돌린 시군구만 센다. */
data class DistrictRepresentativeRefreshResult(
    val selected: Int,
    val cleared: Int,
)
