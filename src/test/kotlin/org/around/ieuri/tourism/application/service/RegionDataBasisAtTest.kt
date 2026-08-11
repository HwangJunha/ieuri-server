package org.around.ieuri.tourism.application.service

import io.mockk.every
import io.mockk.mockk
import org.around.ieuri.tourism.application.policy.RegionRecommendationPolicy
import org.around.ieuri.tourism.application.port.out.query.RegionMetricQueryPort
import org.around.ieuri.tourism.application.query.RecommendedRegionQuery
import org.around.ieuri.tourism.application.query.RegionMetricRow
import org.around.ieuri.tourism.domain.vo.AreaName
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.DistanceScoring
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.FreshnessDecay
import org.around.ieuri.tourism.domain.vo.RegionScoreWeights
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `dataBasisAt` 산출(SPEC-0015 R20 · R24).
 *
 * 지표마다 신선도가 크게 다르다 — 집중률은 오늘(향후 예측), 방문자수는 약 30일 전, 월 지표는
 * 약 2개월 전이다. **가장 최근을 표기하면 "오늘 측정한 데이터"로 읽혀** 실시간이라는 오해를
 * 준다(R24가 금지). 그래서 가장 오래된 기준일을 쓴다.
 */
class RegionDataBasisAtTest {

    private val queryPort = mockk<RegionMetricQueryPort>()

    private val service = RegionRecommendationService(
        regionMetricQueryPort = queryPort,
        policy = RegionRecommendationPolicy(
            weights = RegionScoreWeights.uniform(),
            distanceScoring = DistanceScoring(30_000.0),
            freshnessDecay = FreshnessDecay(plateauDays = 7, halfLifeDays = 23.0),
            minFactorCount = 2,
            defaultLimit = 10,
            maxLimit = 50,
        ),
    )

    private fun row(
        code: String,
        concentrationDate: LocalDate?,
        visitorDate: LocalDate?,
        monthDate: LocalDate?,
    ) = RegionMetricRow(
        code = code,
        name = AreaName(ko = "종로구", en = "Jongno-gu", ja = null, zh = null),
        provinceName = AreaName(ko = "서울특별시", en = "Seoul", ja = null, zh = null),
        center = Coordinates(latitude = 37.6, longitude = 126.98),
        concentrationRate = concentrationDate?.let { 60.0 },
        concentrationBasisDate = concentrationDate,
        visitorCount = visitorDate?.let { 100_000.0 },
        visitorBasisDate = visitorDate,
        stayIntensity = monthDate?.let { 80.0 },
        stayBasisMonth = monthDate,
        spendingIntensity = monthDate?.let { 90.0 },
        spendingBasisMonth = monthDate,
    )

    private fun query() = RecommendedRegionQuery(origin = null, limit = 10)

    @Test
    fun `기준 시점은 가장 오래된 기준일이다 — 최신을 쓰면 실시간으로 오해된다`() {
        every { queryPort.findLatestMetrics() } returns listOf(
            row(
                code = "11110",
                concentrationDate = LocalDate.of(2026, 8, 11), // 오늘(예측)
                visitorDate = LocalDate.of(2026, 7, 12), // 30일 전
                monthDate = LocalDate.of(2026, 6, 1), // 2개월 전
            ),
        )

        val view = service.recommendedRegions(query(), DomainLocale.KO)

        assertEquals(LocalDate.of(2026, 6, 1).atStartOfDay(), view.dataBasisAt)
    }

    @Test
    fun `값이 없는 지표의 기준일은 세지 않는다`() {
        // 월 지표가 결측이면 그 기준일도 후보가 아니다 — 남은 것 중 가장 오래된 날이다.
        every { queryPort.findLatestMetrics() } returns listOf(
            row(
                code = "11110",
                concentrationDate = LocalDate.of(2026, 8, 11),
                visitorDate = LocalDate.of(2026, 7, 12),
                monthDate = null,
            ),
        )

        val view = service.recommendedRegions(query(), DomainLocale.KO)

        assertEquals(LocalDate.of(2026, 7, 12).atStartOfDay(), view.dataBasisAt)
    }

    @Test
    fun `적재된 데이터가 하나도 없으면 기준 시점이 null이고 결과도 비어 있다`() {
        every { queryPort.findLatestMetrics() } returns listOf(
            row(code = "11110", concentrationDate = null, visitorDate = null, monthDate = null),
        )

        val view = service.recommendedRegions(query(), DomainLocale.KO)

        assertNull(view.dataBasisAt)
        assertTrue(view.regions.isEmpty())
    }
}
