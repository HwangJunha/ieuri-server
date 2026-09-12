package org.around.ieuri.tourism.application.policy

import org.around.ieuri.tourism.domain.strategy.DppReranker
import org.around.ieuri.tourism.domain.strategy.RecommendationScore
import org.around.ieuri.tourism.domain.strategy.SpotSimilarity
import java.time.Duration

/**
 * 추천 목록 정책(SPEC-0035 §9, POL-044). 계수는 전부 설정에서 오며 여기 숫자를 적지 않는다.
 *
 * @param candidateCap 후보 상한 N. 거리 가까운 순 N건만 점수·재배열 대상이다(R15).
 * @param cursorMaxAge 커서 수명. 세션 시작 시각 기준이며 이어 받아도 갱신되지 않는다(R4).
 */
data class RecommendationPolicy(
    val score: RecommendationScore,
    val reranker: DppReranker,
    val similarity: SpotSimilarity,
    val candidateCap: Int,
    val cursorMaxAge: Duration,
) {
    init {
        require(candidateCap >= 1) { "후보 상한은 1 이상이어야 합니다." }
        require(!cursorMaxAge.isNegative && !cursorMaxAge.isZero) { "커서 수명은 양수여야 합니다." }
    }
}
