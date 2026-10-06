package org.around.ieuri.tourism.application.shared.provided.dto

import org.around.ieuri.tourism.domain.shared.AreaName
import org.around.ieuri.tourism.domain.shared.Coordinates
import org.around.ieuri.tourism.domain.shared.RegionScore
import org.around.ieuri.tourism.domain.shared.RegionScoreFactor

/** recommendedRegions 쿼리 입력(SPEC-0015). [limit]은 어댑터가 이미 클램프한 값이다. */
data class RecommendedRegionQuery(
    val origin: Coordinates,
    val limit: Int,
)

/** recommendedRegions 응답(R16). */
data class RecommendedRegionView(
    val regions: List<RecommendedRegionItemView>,
)

/**
 * 추천 시군구 1건.
 * - [scores]에는 산출 가능했던 항목만 들어가며 부재 항목을 0점으로 채우지 않는다(POL-043).
 * - 총점은 정렬 키로만 쓰고 내보내지 않는다.
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
 * - [rank]는 1이 가장 좋고, 상한 적용 전 후보 전체에서 매긴다. 동점은 같은 순위, 다음 순위는 건너뛴다.
 */
data class RegionScoreView(
    val score: RegionScore,
    val rank: Int,
) {
    init {
        require(rank >= 1) { "항목 순위는 1 이상이어야 합니다: $rank" }
    }
}

/** 순위가 붙기 전의 항목 점수. */
data class ScoredFactor(
    val score: RegionScore,
)

/** 추천 후보 시군구 1건. [center]는 기하학적 중심이 아니라 배치가 골라 둔 대표 위치다. */
data class RegionRow(
    val code: String,
    val provinceCode: String,
    val name: AreaName,
    val provinceName: AreaName,
    val center: Coordinates,
)

/**
 * origin 좌표가 들어앉은 시군구. 없으면(바다·해외) 추천 결과는 오류가 아니라 빈 목록이다.
 * - 추천에서 [code]는 빠지고, 조회는 [provinceCode]로 좁혀진다.
 */
data class OriginDistrict(
    val code: String,
    val provinceCode: String,
)

/** 한 시군구와 그 지역에서 실제로 값이 있었던 지표들(POL-043). */
data class RegionMetricRow(
    val region: RegionRow,
    /** 항목 → 지표. 결측은 키가 없다(0으로 채우지 않는다). 거리는 요청 시점 계산이라 없다. */
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
 * - [ageDays]는 DB가 한국 날짜로 계산한 경과 일수다. 방문자수만 채우며, null이면 감쇠하지 않는다.
 */
data class RegionMetric(
    val rawValue: Double,
    val ageDays: Long? = null,
)
