package org.around.ieuri.tourism.application.region.provided.dto

import org.around.ieuri.tourism.domain.region.RegionScore

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
