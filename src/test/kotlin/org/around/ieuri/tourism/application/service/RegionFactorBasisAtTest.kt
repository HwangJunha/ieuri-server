package org.around.ieuri.tourism.application.service

import org.around.ieuri.tourism.application.policy.RegionRecommendationPolicy
import org.around.ieuri.tourism.application.query.RecommendedRegionQuery
import org.around.ieuri.tourism.application.query.RegionMetric
import org.around.ieuri.tourism.application.query.RegionRow
import org.around.ieuri.tourism.domain.vo.AreaName
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.DistanceScoring
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.FreshnessDecay
import org.around.ieuri.tourism.domain.vo.RegionScoreFactor
import org.around.ieuri.tourism.domain.vo.RegionScoreWeights
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 항목별 기준일 표기(SPEC-0015 R20 · R24).
 *
 * **응답 전체에 날짜 하나를 두지 않는다.** 지표마다 신선도가 크게 달라(집중률은 오늘 예측,
 * 방문자수는 수십 일 전, 월 지표는 해당 월 1일) 하나로 압축하면 어느 값을 골라도 나머지
 * 항목에 대해 틀린 표기가 된다. 기준일은 각 점수에 실린다.
 */
class RegionFactorBasisAtTest {

    private val policy = RegionRecommendationPolicy(
        weights = RegionScoreWeights.uniform(),
        distanceScoring = DistanceScoring(30_000.0),
        freshnessDecay = FreshnessDecay(plateauDays = 7, halfLifeDays = 23.0),
        minFactorCount = 2,
        defaultLimit = 10,
        maxLimit = 50,
    )

    private fun service(vararg fixtures: RegionFixture) =
        RegionRecommendationService(RegionMetricQueryPortStub(fixtures.toList()), policy)

    private fun fixture(
        code: String,
        concentrationDate: LocalDate?,
        visitorDate: LocalDate?,
        monthDate: LocalDate?,
    ) = RegionFixture(
        region = RegionRow(
            code = code,
            name = AreaName(ko = "종로구", en = "Jongno-gu", ja = null, zh = null),
            provinceName = AreaName(ko = "서울특별시", en = "Seoul", ja = null, zh = null),
            center = Coordinates(latitude = 37.6, longitude = 126.98),
        ),
        metrics = buildMap {
            concentrationDate?.let {
                put(RegionScoreFactor.CONGESTION, RegionMetric(60.0, it))
            }
            visitorDate?.let {
                put(RegionScoreFactor.VISITOR_VOLUME, RegionMetric(100_000.0, it))
            }
            monthDate?.let {
                put(RegionScoreFactor.STAY_INTENSITY, RegionMetric(80.0, it))
                put(RegionScoreFactor.SPENDING_INTENSITY, RegionMetric(90.0, it))
            }
        },
    )

    private fun query() = RecommendedRegionQuery(origin = null, limit = 10)

    @Test
    fun `기준일은 항목마다 자기 값이 실린다 — 하나로 뭉뚱그리지 않는다`() {
        val service = service(
            fixture(
                code = "11110",
                concentrationDate = LocalDate.of(2026, 8, 11), // 오늘(예측)
                visitorDate = LocalDate.of(2026, 7, 12), // 30일 전
                monthDate = LocalDate.of(2026, 6, 1), // 2개월 전
            ),
        )

        val view = service.recommendedRegions(query(), DomainLocale.KO)

        val byFactor = view.regions.single().scores.associate { it.score.factor to it.basisAt }
        assertEquals(LocalDate.of(2026, 8, 11), byFactor[RegionScoreFactor.CONGESTION])
        assertEquals(LocalDate.of(2026, 7, 12), byFactor[RegionScoreFactor.VISITOR_VOLUME])
        assertEquals(LocalDate.of(2026, 6, 1), byFactor[RegionScoreFactor.STAY_INTENSITY])
    }

    @Test
    fun `값이 없는 지표는 점수 목록에 아예 없다`() {
        // 월 지표가 결측이면 그 기준일도 후보가 아니다 — 남은 것 중 가장 오래된 날이다.
        val service = service(
            fixture(
                code = "11110",
                concentrationDate = LocalDate.of(2026, 8, 11),
                visitorDate = LocalDate.of(2026, 7, 12),
                monthDate = null,
            ),
        )

        val view = service.recommendedRegions(query(), DomainLocale.KO)

        val factors = view.regions.single().scores.map { it.score.factor }
        assertFalse(RegionScoreFactor.STAY_INTENSITY in factors)
        assertFalse(RegionScoreFactor.SPENDING_INTENSITY in factors)
    }

    @Test
    fun `적재된 데이터가 하나도 없으면 결과가 비어 있다 — 오류가 아니다`() {
        val service = service(
            fixture(code = "11110", concentrationDate = null, visitorDate = null, monthDate = null),
        )

        val view = service.recommendedRegions(query(), DomainLocale.KO)

        assertTrue(view.regions.isEmpty())
    }
}
