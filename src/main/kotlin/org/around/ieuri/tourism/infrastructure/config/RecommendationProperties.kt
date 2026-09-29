package org.around.ieuri.tourism.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 추천 설정(SPEC-0035 §9 · POL-044). 코드 기본값은 출발값이며 `application.yaml`이 덮어쓴다.
 *
 * @param distanceHalfLifeMeters 거리 요인이 0.5가 되는 거리(m).
 * @param taxRefundTitlePatterns 면세 태그 판정 패턴. 언어별 표기가 다르다.
 * @param categoryRepeatPenalty 같은 카테고리 n번째 장소에 깎는 `penalty × (n − 1)`. 0이면 순수 점수순.
 * @param candidateCap 후보 상한 N. 거리 가까운 순 N건만 대상이다.
 * @param tasteWeight 취향 요인 비중 w. 취향이 있으면 `(1 − w)·거리 + w·취향`.
 * @param tasteMinReactions 취향이 성립하는 최소 반응 수(좋아요 + 싫어요).
 * @param likedMultiplier 좋아요한 장소의 점수 배율(SPEC-0036). 1보다 크면 앞으로 당긴다.
 * @param dislikedMultiplier 싫어요한 장소의 점수 배율. 0이어도 결과에서 빠지지는 않는다.
 */
@ConfigurationProperties(prefix = "ieuri.tourism.recommendation")
data class RecommendationProperties(
    val distanceHalfLifeMeters: Double = 5_000.0,
    val taxRefundMultiplier: Double = 0.8,
    val taxRefundTitlePatterns: List<String> = listOf("[Tax Refund Shop]"),
    val categoryRepeatPenalty: Double = 0.1,
    val candidateCap: Int = 300,
    val tasteWeight: Double = 0.5,
    val tasteMinReactions: Int = 5,
    val likedMultiplier: Double = 1.1,
    val dislikedMultiplier: Double = 0.4,
    val embedding: Embedding = Embedding(),
) {
    /**
     * 임베딩. [dimension]은 `spot_embedding.embedding vector(1024)`와 같아야 한다 — 바꾸면 마이그레이션이 따른다.
     * - [batchSize]는 Voyage 요청당 입력 상한(1,000건·320K 토큰) 안이어야 한다.
     */
    data class Embedding(
        val model: String = "voyage-4",
        val dimension: Int = 1024,
        val batchSize: Int = 128,
        /** 제공자 실패 시 즉시 재시도 횟수(POL-040). */
        val retryCount: Int = 3,
        /** 임베딩 텍스트에 넣는 소개문(spot_detail.overview) 길이 상한(자). 문장 끝에서 자른다. */
        val overviewMaxChars: Int = 500,
    )
}
