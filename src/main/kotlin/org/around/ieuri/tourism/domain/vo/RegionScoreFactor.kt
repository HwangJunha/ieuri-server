package org.around.ieuri.tourism.domain.vo

/**
 * 시군구 추천 점수를 구성하는 항목(SPEC-0015 R10). 번역하지 않는 식별자다(POL-023).
 * - [inverted]는 원본 값이 클수록 점수가 낮다는 뜻이며 [CONGESTION]만 그렇다.
 */
enum class RegionScoreFactor(val inverted: Boolean) {
    /** 예상 혼잡도. 실시간이 아니라 예측값이다. 덜 붐빌수록 높은 점수다. */
    CONGESTION(inverted = true),

    /** 방문자 규모. 일별 순 방문자 수 기반. */
    VISITOR_VOLUME(inverted = false),

    /** 체류 강도. 월 단위 집계. */
    STAY_INTENSITY(inverted = false),

    /** 소비 강도. 외지인 소비 기준이며 외국인 소비는 포함하지 않는다. */
    SPENDING_INTENSITY(inverted = false),

    /** 거리. 요청 시점에 origin으로부터 계산된다. */
    DISTANCE(inverted = false),
}
