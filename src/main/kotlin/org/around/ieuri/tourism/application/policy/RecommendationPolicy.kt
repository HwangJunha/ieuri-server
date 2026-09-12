package org.around.ieuri.tourism.application.policy

import org.around.ieuri.tourism.domain.strategy.CategoryDiversity
import org.around.ieuri.tourism.domain.strategy.RecommendationScore

/**
 * 추천 정책(SPEC-0035 §9, POL-044). 계수는 전부 설정에서 오며 여기 숫자를 적지 않는다.
 *
 * @param candidateCap 후보 상한 N. 거리 가까운 순 N건만 점수·재배열 대상이다.
 * @param tasteMinReactions 취향 요인이 성립하는 최소 반응 수(좋아요 + 싫어요). 미만이면 요인 없음.
 */
data class RecommendationPolicy(
    val score: RecommendationScore,
    val diversity: CategoryDiversity,
    val candidateCap: Int,
    val tasteMinReactions: Int,
    val embedding: EmbeddingSpec,
) {
    init {
        require(candidateCap >= 1) { "후보 상한은 1 이상이어야 합니다." }
        require(tasteMinReactions >= 1) { "취향 최소 반응 수는 1 이상이어야 합니다." }
    }
}

/**
 * 임베딩 사양. [model]·[dimension]이 저장된 벡터와 다르면 그 벡터는 없는 것으로 본다 —
 * 모델 교체가 곧 재적재 대상 판정이다.
 */
data class EmbeddingSpec(
    val model: String,
    val dimension: Int,
    /** 제공자 실패 시 즉시 재시도 횟수. 시간 기반 대기는 없다(POL-040). */
    val retryCount: Int,
    /** 임베딩 텍스트에 넣는 소개문 길이 상한(자). 토큰 비용의 천장이다. */
    val overviewMaxChars: Int,
) {
    init {
        require(model.isNotBlank()) { "임베딩 모델이 비어 있습니다." }
        require(dimension >= 1) { "임베딩 차원은 1 이상이어야 합니다." }
        require(retryCount >= 0) { "재시도 횟수는 음수일 수 없습니다." }
        require(overviewMaxChars >= 0) { "소개문 길이 상한은 음수일 수 없습니다." }
    }
}
