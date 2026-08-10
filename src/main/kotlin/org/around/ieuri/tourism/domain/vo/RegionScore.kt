package org.around.ieuri.tourism.domain.vo

/**
 * 한 항목의 정규화된 점수(SPEC-0015 R11). 값은 항상 0~100이다 — 단위가 다른 원본 값을
 * 그대로 더하지 않기 위해 여기서 척도를 맞춘다.
 *
 * **부재 항목은 이 타입으로 표현하지 않는다.** 값이 없으면 [RegionScore] 자체를 만들지 않고
 * 산출 대상에서 뺀다(POL-043) — 0점 인스턴스는 부재가 아니라 "측정된 최저값"을 뜻한다.
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
