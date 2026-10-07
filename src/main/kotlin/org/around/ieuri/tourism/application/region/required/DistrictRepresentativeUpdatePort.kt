package org.around.ieuri.tourism.application.region.required

import org.around.ieuri.tourism.domain.region.RepresentativeLocation

/** 시군구 대표 위치 저장(SPEC-0015). 고르는 규칙은 도메인([RepresentativeLocation])에 있다. */
interface DistrictRepresentativeUpdatePort {

    /**
     * 시군구 코드 → 그 경계 안(경계선 포함)의 KO 원본 좌표.
     * - KO 행만 센다 — 언어별 행을 세면 번역이 많은 지역이 부풀는다.
     */
    fun findKoreanSpotPoints(): Map<String, List<RepresentativeLocation.Point>>

    /** 대표 위치를 그 원본의 위치로 쓴다. */
    fun saveRepresentative(districtCode: String, choice: RepresentativeLocation.Choice)

    /** [selectedCodes]에 없는 시군구의 대표 위치를 비우고, 이번에 값을 잃은 수를 돌려준다. */
    fun clearRepresentativesExcept(selectedCodes: Set<String>): Int
}

/** 대표 위치 갱신 결과. [cleared]는 이전에 값이 있다가 비운 시군구만 센다. */
data class DistrictRepresentativeRefreshResult(
    val selected: Int,
    val cleared: Int,
)
