package org.around.ieuri.tourism.application.policy

import org.around.ieuri.tourism.domain.strategy.DppReranker
import org.around.ieuri.tourism.domain.strategy.RecommendationScore
import org.around.ieuri.tourism.domain.strategy.SpotSimilarity
import org.around.ieuri.tourism.domain.vo.FeedCursorSecret
import java.time.Duration

/**
 * 추천 피드 정책(SPEC-0035 §9, POL-044). 계수는 전부 설정에서 오며 여기 숫자를 적지 않는다.
 *
 * @param candidateCap 후보 상한 N. 거리 가까운 순 N건만 점수·재배열 대상이다(R15).
 * @param batchSize 묶음 크기 B. 순열은 B건씩 확정되고 캐시된다(R15·R16). **묶음 수는 ⌈N ÷ B⌉이고
 *   캐시가 비면 b번째 묶음을 만들기 위해 앞 묶음 b개를 다시 계산한다** — N을 키울 때는 B도 함께 본다.
 * @param cursorMaxAge 커서 수명. 발급 시각 기준이며 이어 받아도 갱신되지 않는다(R4).
 */
data class RecommendationPolicy(
    val score: RecommendationScore,
    val reranker: DppReranker,
    val similarity: SpotSimilarity,
    val candidateCap: Int,
    val batchSize: Int,
    val cursorMaxAge: Duration,
    val cursorSecret: FeedCursorSecret,
) {
    init {
        require(candidateCap >= 1) { "후보 상한은 1 이상이어야 합니다." }
        require(batchSize >= 1) { "묶음 크기는 1 이상이어야 합니다." }
        require(!cursorMaxAge.isNegative && !cursorMaxAge.isZero) { "커서 수명은 양수여야 합니다." }
    }
}
