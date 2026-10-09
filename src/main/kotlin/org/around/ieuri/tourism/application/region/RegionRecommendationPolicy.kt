package org.around.ieuri.tourism.application.region

import org.around.ieuri.tourism.domain.region.RegionScoring
import org.around.ieuri.tourism.domain.region.spec.ProvinceEligibility

/**
 * 지역 추천 정책값(SPEC-0015 R19·R30, 설정에서 주입).
 *
 * @param scoring 점수 모델 — 가중치·거리 반감·신선도 감쇠·최소 항목 수(R30, 거리도 한 항목)를 품는다.
 * @param provinceEligibility 시도가 후보로 남기 위한 최소 시군구 수.
 * @param maxLimit 개수 상한(POL-018). 초과는 클램프, 0 이하만 거절이다(R29).
 */
data class RegionRecommendationPolicy(
    val scoring: RegionScoring,
    val provinceEligibility: ProvinceEligibility,
    val defaultLimit: Int,
    val maxLimit: Int,
) {
    init {
        require(defaultLimit in 1..maxLimit) { "기본 개수는 1 이상 상한 이하여야 합니다: $defaultLimit" }
    }

    /** null(미지정)은 기본값, 상한 초과는 상한으로 자른다. 0 이하 판정은 호출자(어댑터)의 몫이다. */
    fun resolveLimit(requested: Int?): Int = (requested ?: defaultLimit).coerceAtMost(maxLimit)
}
