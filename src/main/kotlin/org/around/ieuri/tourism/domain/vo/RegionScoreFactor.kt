package org.around.ieuri.tourism.domain.vo

/**
 * 시군구 추천 점수를 구성하는 항목(SPEC-0015 R10).
 *
 * 표시 문자열이 아니라 식별자다(POL-023) — 번역하지 않는다.
 *
 * [CONGESTION]만 **원본 값이 클수록 점수가 낮다**(덜 붐빌수록 높다). 나머지 넷은 원본 값이
 * 클수록 점수가 높다. 정규화가 이 방향을 [inverted]로 판단한다.
 */
enum class RegionScoreFactor(val inverted: Boolean) {
    /** 예상 혼잡도. 향후 30일 예측이며 실시간이 아니다. 덜 붐빌수록 높은 점수다. */
    CONGESTION(inverted = true),

    /** 방문자 규모. 일별 순 방문자 수 기반. */
    VISITOR_VOLUME(inverted = false),

    /** 체류 강도. 월 단위 집계. */
    STAY_INTENSITY(inverted = false),

    /** 소비 강도. 외지인 소비 기준이며 외국인 소비는 포함하지 않는다. */
    SPENDING_INTENSITY(inverted = false),

    /** 거리. 사용자 위치가 없으면 이 항목 자체가 부재다(R21). */
    DISTANCE(inverted = false),
}
