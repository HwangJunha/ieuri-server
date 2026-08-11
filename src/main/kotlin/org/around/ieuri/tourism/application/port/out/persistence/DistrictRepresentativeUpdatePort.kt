package org.around.ieuri.tourism.application.port.out.persistence

/**
 * 시군구 대표 위치 갱신 포트(SPEC-0015 · V46).
 *
 * 선정은 전 시군구를 한 번에 훑는 집합 연산이라 개별 시군구를 넘기는 형태가 아니다 —
 * 117k행을 애플리케이션으로 끌어오지 않고 DB 안에서 끝낸다.
 *
 * @param cellSizeDegrees 밀집 격자 한 칸의 크기(도).
 * @param minSpotCount 선정 문턱. 미달 시군구는 NULL로 되돌린다.
 */
interface DistrictRepresentativeUpdatePort {

    fun refreshRepresentativeLocations(cellSizeDegrees: Double, minSpotCount: Int): DistrictRepresentativeRefreshResult
}

/**
 * @param selected 대표 위치가 채워진 시군구 수.
 * @param cleared 문턱 미달로 NULL로 되돌린 시군구 수. **이전 실행에서 값이 있었던 것만 센다** —
 *   원래 NULL이던 시군구는 포함되지 않으므로, 이 값이 크면 spot이 실제로 줄었다는 신호다.
 */
data class DistrictRepresentativeRefreshResult(
    val selected: Int,
    val cleared: Int,
)
