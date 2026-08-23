package org.around.ieuri.tourism.application.query

import org.around.ieuri.tourism.domain.vo.AreaName
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.RegionScore
import org.around.ieuri.tourism.domain.vo.RegionScoreFactor

/**
 * recommendedRegions 쿼리 입력(SPEC-0015 R19·R21).
 *
 * [limit]은 어댑터가 이미 검증·클램프한 값이라 여기서는 양수 상한 이하임이 보장된다.
 */
data class RecommendedRegionQuery(
    val origin: Coordinates,
    val limit: Int,
)

/** recommendedRegions 응답(R16). */
data class RecommendedRegionView(
    val regions: List<RecommendedRegionItemView>,
)

/**
 * 추천 시군구 1건(R17·R18·R31).
 *
 * [scores]에는 산출 가능했던 항목만 들어간다 — 부재 항목은 0점으로 채우지 않고 빠진다
 * (POL-043). [scoreFactorCount]가 그 개수이며 지역마다 다를 수 있다.
 *
 * **총점은 여기 없다** — 정렬 키로만 쓰고 계약에 내보내지 않는다(R16).
 */
data class RecommendedRegionItemView(
    val code: String,
    val name: String,
    val provinceName: String,
    val scores: List<RegionScoreView>,
    val scoreFactorCount: Int,
    val center: Coordinates,
)

/**
 * 항목 점수 1개와 그 항목에서의 순위.
 *
 * 순위를 도메인 [RegionScore]에 넣지 않는 것은 한 지역이 아니라 **후보 집합 전체**에서만
 * 정해지는 값이기 때문이다.
 *
 * [rank]는 **1이 가장 좋고**(= [RegionScore.value]가 가장 높음), 상한을 적용하기 전의 후보
 * 전체에서 매겨진다. 동점은 같은 순위를 받고 다음 순위를 건너뛴다(표준 경쟁 순위).
 */
data class RegionScoreView(
    val score: RegionScore,
    val rank: Int,
) {
    init {
        require(rank >= 1) { "항목 순위는 1 이상이어야 합니다: $rank" }
    }
}

/**
 * 순위가 붙기 전의 항목 점수 — 순위는 후보 집합이 확정된 뒤에야 정해진다.
 *
 * [RegionScoreView]를 임시값으로 먼저 만들면 순위를 못 붙인 인스턴스가 밖으로 샐 수 있어,
 * 순위 이전 단계를 아예 다른 타입으로 둔다.
 */
data class ScoredFactor(
    val score: RegionScore,
)

/**
 * 추천 후보 시군구 1건. 지표는 여기 없다 — 지표별 질의가 코드로 따로 붙인다.
 *
 * [center]는 기하학적 중심이 아니라 배치가 골라 둔 **대표 위치**다. 대표 위치가 없는
 * 시군구는 후보에서 빠지므로 이 값은 항상 존재한다.
 */
data class RegionRow(
    val code: String,
    val provinceCode: String,
    val name: AreaName,
    val provinceName: AreaName,
    val center: Coordinates,
)

/**
 * origin 좌표가 실제로 들어앉은 시군구. 경계 안에 있는 지역이 없으면 이 값 자체가 null이며
 * (바다·해외) 그때 추천 결과는 빈 목록이다 — 오류가 아니다.
 *
 * 추천에서 이 지역은 빠지고([code]), 조회 자체가 이 시도로 좁혀진다([provinceCode]).
 */
data class OriginDistrict(
    val code: String,
    val provinceCode: String,
)

/**
 * 한 시군구와 그 지역에서 **실제로 값이 있었던** 지표들(R14 · POL-043).
 *
 * 지역 전건과 지표별 맵을 코드에서 결합한 결과다.
 */
data class RegionMetricRow(
    val region: RegionRow,
    /**
     * 항목 → 지표. **값이 있는 항목만** 들어간다 — 결측은 키 자체가 없고 0으로 채우지
     * 않는다(R14 · POL-043). 거리는 여기 없다: 요청 시점 계산이다.
     */
    val metrics: Map<RegionScoreFactor, RegionMetric>,
) {
    val code: String get() = region.code
    val provinceCode: String get() = region.provinceCode
    val name: AreaName get() = region.name
    val provinceName: AreaName get() = region.provinceName
    val center: Coordinates get() = region.center
}

/**
 * 한 항목의 정규화 이전 원본 값.
 *
 * [ageDays]는 근거 데이터의 경과 일수이며 **DB가 한국 날짜로 계산해 넘긴다** — 앱 시계로
 * 재면 DB가 UTC라 한국 00~09시 구간에서 하루가 어긋난다. null이면 감쇠하지 않는다(나이를
 * 모르면 깎을 근거가 없다). 신선도 감쇠를 쓰는 방문자수만 채운다.
 */
data class RegionMetric(
    val rawValue: Double,
    val ageDays: Long? = null,
)
