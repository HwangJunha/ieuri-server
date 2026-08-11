package org.around.ieuri.tourism.domain.vo

import kotlin.math.pow

/**
 * 오래된 지표의 점수를 낮추는 신선도 감쇠(SPEC-0015 · POL-044).
 *
 * **가중치가 아니라 정규화된 점수에 곱한다.** 가중치를 깎으면 그 항목이 분모에서도 함께
 * 줄어 총점이 거의 그대로다 — 낡은 데이터가 아무 대가 없이 통과한다. 점수를 깎아야 순위가
 * 실제로 내려간다.
 *
 * [plateauDays]까지는 감쇠가 없다(계수 1.0). 그 이후 [halfLifeDays]마다 절반이 된다.
 *
 * @param plateauDays 감쇠가 시작되지 않는 경과 일수. 이 값 이하는 계수 1.0이다.
 * @param halfLifeDays 반감기(일). [plateauDays] 이후 이 일수마다 계수가 절반이 된다.
 */
data class FreshnessDecay(
    val plateauDays: Long,
    val halfLifeDays: Double,
) {
    init {
        require(plateauDays >= 0) { "감쇠 유예 일수는 0 이상이어야 합니다: $plateauDays" }
        require(halfLifeDays > 0.0) { "반감기는 양수여야 합니다: $halfLifeDays" }
    }

    /** 경과 일수에 대한 감쇠 계수(0~1]. 미래 날짜(음수 경과)도 유예 구간과 같이 1.0이다. */
    fun factor(ageDays: Long): Double =
        if (ageDays <= plateauDays) 1.0 else 0.5.pow((ageDays - plateauDays) / halfLifeDays)

    /** 점수에 계수를 곱한 새 점수. 항목은 그대로 유지된다 — 감쇠는 값만 낮춘다. */
    fun apply(score: RegionScore, ageDays: Long): RegionScore =
        score.copy(value = score.value * factor(ageDays))
}
