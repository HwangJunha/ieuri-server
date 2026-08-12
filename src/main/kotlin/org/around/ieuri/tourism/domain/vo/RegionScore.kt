package org.around.ieuri.tourism.domain.vo

/**
 * 한 항목의 정규화된 점수(SPEC-0015 R11). 값은 항상 0~100이다.
 *
 * **부재 항목은 이 타입으로 표현하지 않는다** — 0점은 부재가 아니라 "측정된 최저값"이다.
 * 값이 없으면 인스턴스를 만들지 않고 산출 대상에서 뺀다(POL-043).
 */
data class RegionScore(
    val factor: RegionScoreFactor,
    val value: Double,
) {
    init {
        require(value in MIN_VALUE..MAX_VALUE) { "항목 점수는 0~100 범위여야 합니다: $value" }
    }

    companion object {
        const val MIN_VALUE = 0.0
        const val MAX_VALUE = 100.0
    }
}
