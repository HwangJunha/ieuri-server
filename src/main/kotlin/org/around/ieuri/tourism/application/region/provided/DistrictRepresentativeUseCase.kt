package org.around.ieuri.tourism.application.region.provided

import org.around.ieuri.tourism.application.region.provided.dto.DistrictRepresentativeRefreshResult

/** 시군구 대표 위치를 다시 고른다(SPEC-0015). 원본 동기화가 끝난 뒤 배치가 한 번 부른다. */
interface DistrictRepresentativeUseCase {
    /** 격자 크기는 도 단위이며, 가장 밀집한 격자가 [minSpotCount]에 못 미치는 시군구는 대표 위치를 비운다. */
    fun refreshRepresentativeLocations(cellSizeDegrees: Double, minSpotCount: Int): DistrictRepresentativeRefreshResult
}
