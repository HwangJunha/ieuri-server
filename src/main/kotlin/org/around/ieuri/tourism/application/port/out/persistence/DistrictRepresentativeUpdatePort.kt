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

    /**
     * 시군구별 카테고리 구성비를 다시 집계한다(SPEC-0015 · V50).
     *
     * 대표 위치와 **같은 포트에 있는 이유**: 둘 다 `spot`을 시군구 경계로 공간 조인해 산출하며
     * 같은 시점의 데이터로 계산되어야 한다. 갱신 시점이 갈리면 "대표 위치는 있는데 카테고리
     * 집계는 어제 것"인 시군구가 생긴다.
     *
     * 경계 안에 KO spot이 하나도 없는 시군구는 **NULL로 되돌린다 — 0으로 채우지 않는다**
     * (POL-043: 없는 데이터와 0건은 다르다).
     *
     * @return 집계가 갱신된 시군구 수.
     */
    fun refreshCategoryMix(): Int
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
