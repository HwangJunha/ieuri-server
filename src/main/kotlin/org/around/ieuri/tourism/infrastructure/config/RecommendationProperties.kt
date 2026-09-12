package org.around.ieuri.tourism.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 추천 설정(SPEC-0035 §9 · POL-044). 코드 기본값은 **출발값**이며 실측 후 확정한다.
 * 값은 `application.yaml`이 덮어쓴다.
 *
 * @param distanceHalfLifeMeters 거리 요인이 0.5가 되는 거리(m). SPEC-0013 실측 5km.
 * @param taxRefundMultiplier 면세 태그 장소의 점수 배율.
 * @param taxRefundTitlePatterns 면세 태그 판정 패턴. 언어별 표기가 다르다(실측: EN·JA·ZH·KO 네 가지).
 * @param categoryRepeatPenalty 같은 카테고리 n번째 장소에 깎는 `penalty × (n − 1)`. 0이면 순수 점수순.
 * @param candidateCap 후보 상한 N. 거리 가까운 순 N건만 대상이다.
 */
@ConfigurationProperties(prefix = "ieuri.tourism.recommendation")
data class RecommendationProperties(
    val distanceHalfLifeMeters: Double = 5_000.0,
    val taxRefundMultiplier: Double = 0.8,
    val taxRefundTitlePatterns: List<String> = listOf("[Tax Refund Shop]"),
    val categoryRepeatPenalty: Double = 0.1,
    val candidateCap: Int = 300,
)
