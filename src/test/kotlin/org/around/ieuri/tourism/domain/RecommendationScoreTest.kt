package org.around.ieuri.tourism.domain

import org.around.ieuri.tourism.domain.specification.TaxRefundTitleRule
import org.around.ieuri.tourism.domain.strategy.RecommendationScore
import org.around.ieuri.tourism.domain.vo.ScoreFactors
import org.around.ieuri.tourism.domain.vo.ScoreWeights
import kotlin.math.ln
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** SPEC-0035 R6~R12 — 요인 정규화·가중합·분모 축소·면세 배율. */
class RecommendationScoreTest {

    private val weights = ScoreWeights(distance = 0.4, popularity = 0.25, activity = 0.15, affinity = 0.2, trend = 0.0)

    private fun score(
        weights: ScoreWeights = this.weights,
        taxRefundMultiplier: Double = 0.8,
        patterns: List<String> = listOf("[Tax Refund Shop]"),
    ) = RecommendationScore(
        weights = weights,
        distanceHalfLifeMeters = 5_000.0,
        popularityCap = 100,
        activityCap = 50,
        taxRefundMultiplier = taxRefundMultiplier,
        taxRefundTitleRule = TaxRefundTitleRule(patterns),
    )

    @Test
    fun `거리와 인기만 있으면 두 가중치로만 나눈다 - AC-6_1`() {
        val factors = ScoreFactors(distance = 0.8, popularity = 0.4)
        val expected = (0.4 * 0.8 + 0.25 * 0.4) / (0.4 + 0.25)
        assertEquals(expected, score().score(factors, "카페"))
    }

    @Test
    fun `요인이 하나도 없으면 점수는 없음이지 0이 아니다 - AC-6_2`() {
        assertNull(score().score(ScoreFactors(), "카페"))
    }

    @Test
    fun `가중치 설정이 바뀌면 같은 입력의 결과가 달라진다 - AC-6_3`() {
        val factors = ScoreFactors(distance = 1.0, popularity = 0.0)
        val distanceHeavy = score(ScoreWeights(0.9, 0.1, 0.0, 0.0, 0.0)).score(factors, "카페")
        val popularityHeavy = score(ScoreWeights(0.1, 0.9, 0.0, 0.0, 0.0)).score(factors, "카페")
        assertEquals(0.9, distanceHeavy!!, 1e-9)
        assertEquals(0.1, popularityHeavy!!, 1e-9)
    }

    @Test
    fun `거리 요인은 0에서 1, 반감 거리에서 0_5, 세 배에서 0_25다 - AC-7_1`() {
        assertEquals(1.0, score().distanceFactor(0.0), 1e-9)
        assertEquals(0.5, score().distanceFactor(5_000.0), 1e-9)
        assertEquals(0.25, score().distanceFactor(15_000.0), 1e-9)
    }

    @Test
    fun `나머지가 같으면 가까운 쪽 점수가 높다 - AC-7_2`() {
        val near = score().score(ScoreFactors(distance = score().distanceFactor(1_000.0), popularity = 0.3), "a")!!
        val far = score().score(ScoreFactors(distance = score().distanceFactor(9_000.0), popularity = 0.3), "b")!!
        assertTrue(near > far)
    }

    @Test
    fun `인기 요인은 0에서 0, 상한에서 1, 초과도 1이다 - AC-8_1`() {
        assertEquals(0.0, score().popularityFactor(0, 0), 1e-9)
        assertEquals(1.0, score().popularityFactor(100, 0), 1e-9)
        assertEquals(1.0, score().popularityFactor(1_000, 0), 1e-9)
    }

    @Test
    fun `싫어요가 더 많으면 인기 요인은 0이다 - AC-8_2`() {
        assertEquals(0.0, score().popularityFactor(3, 5), 1e-9)
    }

    @Test
    fun `활동 요인은 글·질문·답변 합의 로그 비율이다 - AC-9_1`() {
        assertEquals(ln(7.0) / ln(51.0), score().activityFactor(2, 1, 3), 1e-9)
    }

    @Test
    fun `활동 합이 상한 이상이면 1이다 - AC-9_2`() {
        assertEquals(1.0, score().activityFactor(50, 0, 0), 1e-9)
        assertEquals(1.0, score().activityFactor(40, 20, 0), 1e-9)
    }

    @Test
    fun `취향 요인은 코사인을 0~1로 옮긴다 - AC-10_1`() {
        assertEquals(1.0, score().affinityFactor(1.0), 1e-9)
        assertEquals(0.5, score().affinityFactor(0.0), 1e-9)
        assertEquals(0.0, score().affinityFactor(-1.0), 1e-9)
    }

    @Test
    fun `태그 장소는 가중합에 배율을 곱한다 - AC-11_1`() {
        val plain = score().score(ScoreFactors(distance = 0.5), "가산 아울렛")!!
        val tagged = score().score(ScoreFactors(distance = 0.5), "[Tax Refund Shop] 가산 아울렛")!!
        assertEquals(plain * 0.8, tagged, 1e-9)
    }

    @Test
    fun `배율 1이면 감점이 없다 - AC-11_2`() {
        val plain = score(taxRefundMultiplier = 1.0).score(ScoreFactors(distance = 0.5), "가산 아울렛")!!
        val tagged = score(taxRefundMultiplier = 1.0).score(ScoreFactors(distance = 0.5), "[Tax Refund Shop] 가산 아울렛")!!
        assertEquals(plain, tagged, 1e-9)
    }

    @Test
    fun `패턴이 없는 언어의 제목은 감점 없이 계산되고 오류가 아니다 - AC-11_4`() {
        val result = score(patterns = emptyList()).score(ScoreFactors(distance = 0.5), "[Tax Refund Shop] 가산")
        assertNotNull(result)
        assertEquals(0.5, result, 1e-9)
    }

    @Test
    fun `유행 값이 없으면 나머지 요인으로만 계산한다 - AC-12_2`() {
        val without = score().score(ScoreFactors(distance = 0.5, activity = 0.2), "a")
        val expected = (0.4 * 0.5 + 0.15 * 0.2) / (0.4 + 0.15)
        assertEquals(expected, without!!, 1e-9)
    }

    @Test
    fun `유행 값이 있으면 그 가중치로 반영된다 - AC-12_1`() {
        val trendWeighted = score(ScoreWeights(0.5, 0.0, 0.0, 0.0, 0.5))
        val result = trendWeighted.score(ScoreFactors(distance = 0.2, trend = 1.0), "a")
        assertEquals(0.6, result!!, 1e-9)
    }
}
