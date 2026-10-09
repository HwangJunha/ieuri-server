package org.around.ieuri.tourism.domain.region.policy

import org.around.ieuri.tourism.domain.region.RegionScore
import org.around.ieuri.tourism.domain.region.RegionScoreFactor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 신선도 감쇠(SPEC-0015). 유예 7일 · 반감기 23일은 7일에 1.0, 30일에 0.5를 만든다. */
class FreshnessDecayTest {

    private val decay = FreshnessDecay(plateauDays = 7, halfLifeDays = 23.0)

    @Test
    fun `유예 기간 안에서는 감쇠하지 않는다`() {
        assertEquals(1.0, decay.factor(0))
        assertEquals(1.0, decay.factor(3))
        // 경계값(정확히 7일)은 유예에 포함된다.
        assertEquals(1.0, decay.factor(7))
    }

    @Test
    fun `유예를 하루 넘기면 곧바로 감쇠가 시작된다`() {
        assertTrue(decay.factor(8) < 1.0, "8일은 유예 밖이라 1.0 미만이어야 한다")
    }

    @Test
    fun `30일 경과에서 정확히 절반이 된다 — 반감기 23일을 고른 이유다`() {
        assertEquals(0.5, decay.factor(30), 1e-9)
    }

    @Test
    fun `반감기가 한 번 더 지나면 다시 절반이 된다`() {
        assertEquals(0.25, decay.factor(53), 1e-9)
    }

    @Test
    fun `오래될수록 단조 감소한다`() {
        val factors = (7L..90L).map { decay.factor(it) }
        assertTrue(
            factors.zipWithNext().all { (earlier, later) -> later <= earlier },
            "경과 일수가 늘수록 계수가 커지는 구간이 있으면 안 된다",
        )
    }

    @Test
    fun `미래 기준일도 감쇠하지 않는다`() {
        // 미래 날짜여도 계수가 1을 넘지 않는다.
        assertEquals(1.0, decay.factor(-5))
    }

    @Test
    fun `감쇠는 점수 값만 낮추고 항목은 그대로 둔다`() {
        val score = RegionScore(RegionScoreFactor.VISITOR_VOLUME, 80.0)

        val decayed = decay.apply(score, ageDays = 30)

        assertEquals(RegionScoreFactor.VISITOR_VOLUME, decayed.factor)
        assertEquals(40.0, decayed.value, 1e-9)
    }

    @Test
    fun `유예 안의 점수는 값이 보존된다`() {
        val score = RegionScore(RegionScoreFactor.VISITOR_VOLUME, 80.0)

        assertEquals(80.0, decay.apply(score, ageDays = 7).value, 1e-9)
    }

    @Test
    fun `반감기가 0 이하면 계수를 정의할 수 없어 거절한다`() {
        assertFailsWith<IllegalArgumentException> { FreshnessDecay(plateauDays = 7, halfLifeDays = 0.0) }
        assertFailsWith<IllegalArgumentException> { FreshnessDecay(plateauDays = 7, halfLifeDays = -1.0) }
    }

    @Test
    fun `유예 일수는 음수일 수 없다`() {
        assertFailsWith<IllegalArgumentException> { FreshnessDecay(plateauDays = -1, halfLifeDays = 23.0) }
    }
}
