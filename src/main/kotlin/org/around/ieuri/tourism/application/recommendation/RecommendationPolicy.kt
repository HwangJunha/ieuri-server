package org.around.ieuri.tourism.application.recommendation

import org.around.ieuri.tourism.domain.recommendation.SpotRanking
import org.around.ieuri.tourism.domain.recommendation.spec.TasteThreshold

/**
 * 추천 정책(SPEC-0035 §9, POL-044). 계수는 전부 설정에서 오며 여기 숫자를 적지 않는다.
 *
 * @param candidateCap 후보 상한 N. 거리 가까운 순 N건만 점수·재배열 대상이다.
 * @param tasteThreshold 취향 요인이 성립하는 최소 반응 수. 미만이면 요인 없음.
 */
data class RecommendationPolicy(
    val ranking: SpotRanking,
    val candidateCap: Int,
    val tasteThreshold: TasteThreshold,
    val embedding: EmbeddingSpec,
) {
    init {
        require(candidateCap >= 1) { "후보 상한은 1 이상이어야 합니다." }
    }
}
