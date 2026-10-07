package org.around.ieuri.tourism.application.region.provided.dto

import org.around.ieuri.tourism.domain.region.RegionScore
import org.around.ieuri.tourism.domain.shared.Coordinates

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
