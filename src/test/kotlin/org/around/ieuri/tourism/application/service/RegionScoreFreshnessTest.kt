package org.around.ieuri.tourism.application.service

import org.around.ieuri.tourism.application.policy.RegionRecommendationPolicy
import org.around.ieuri.tourism.application.query.RecommendedRegionItemView
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
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 항목별 기준일 노출과 신선도 감쇠(SPEC-0015).
 *
 * 감쇠는 **방문자수에만** 걸린다 — 적재 지연이 실제로 관측된 유일한 항목이다. 다른 항목에
 * 같은 감쇠를 걸면 월 지표가 정상 주기만으로 반값이 된다.
 */
class RegionScoreFreshnessTest {

    /** 오늘은 2026-08-11(KST)이다. 나이는 DB가 계산해 넘기므로 픽스처가 직접 싣는다. */
    private val today = LocalDate.of(2026, 8, 11)

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
        visitorCount: Double?,
        visitorDate: LocalDate?,
        visitorAgeDays: Long? = visitorDate?.let { ChronoUnit.DAYS.between(it, today) },
        concentrationRate: Double? = 50.0,
        stayIntensity: Double? = 70.0,
    ) = RegionFixture(
        region = RegionRow(
            code = code,
            name = AreaName(ko = "강남구", en = "Gangnam-gu", ja = null, zh = null),
            provinceName = AreaName(ko = "서울특별시", en = "Seoul", ja = null, zh = null),
            center = Coordinates(latitude = 37.5, longitude = 127.05),
        ),
        metrics = buildMap {
            concentrationRate?.let {
                put(RegionScoreFactor.CONGESTION, RegionMetric(it, today))
            }
            visitorCount?.let {
                put(
                    RegionScoreFactor.VISITOR_VOLUME,
                    RegionMetric(it, visitorDate, ageDays = visitorAgeDays),
                )
            }
            stayIntensity?.let {
                put(RegionScoreFactor.STAY_INTENSITY, RegionMetric(it, LocalDate.of(2026, 6, 1)))
            }
        },
    )

    private fun query(origin: Coordinates? = null) = RecommendedRegionQuery(origin = origin, limit = 10)

    private fun RecommendedRegionItemView.score(factor: RegionScoreFactor) =
        scores.single { it.score.factor == factor }

    @Test
    fun `항목마다 자기 기준일이 실린다 — 하나의 날짜로 뭉뚱그리지 않는다`() {
        val service = service(
            fixture(code = "11680", visitorCount = 633_341.0, visitorDate = LocalDate.of(2026, 7, 12)),
        )

        val region = service.recommendedRegions(query(), DomainLocale.KO).regions.single()

        assertEquals(today, region.score(RegionScoreFactor.CONGESTION).basisAt)
        assertEquals(LocalDate.of(2026, 7, 12), region.score(RegionScoreFactor.VISITOR_VOLUME).basisAt)
        assertEquals(LocalDate.of(2026, 6, 1), region.score(RegionScoreFactor.STAY_INTENSITY).basisAt)
    }

    @Test
    fun `가장 오래된 항목은 월 지표다 — 항목마다 신선도가 다르다`() {
        val service = service(
            fixture(code = "11680", visitorCount = 633_341.0, visitorDate = LocalDate.of(2026, 7, 12)),
        )

        val view = service.recommendedRegions(query(), DomainLocale.KO)

        val oldest = view.regions.single().scores.mapNotNull { it.basisAt }.min()
        assertEquals(LocalDate.of(2026, 6, 1), oldest)
    }

    @Test
    fun `거리 항목에는 기준일이 없다 — 적재된 값이 아니라 요청 시점 계산이다`() {
        val service = service(
            fixture(code = "11680", visitorCount = 633_341.0, visitorDate = today),
        )

        val region = service
            .recommendedRegions(query(origin = Coordinates(37.5, 127.05)), DomainLocale.KO)
            .regions
            .single()

        assertNull(region.score(RegionScoreFactor.DISTANCE).basisAt)
    }

    @Test
    fun `30일 지난 방문자수는 점수가 절반이 된다`() {
        // 두 지역의 원본 방문자수가 같고 기준일만 다르다 — 정규화 후 점수는 둘 다 만점이므로
        // 차이는 오직 감쇠에서 온다.
        val service = service(
            fixture(code = "11680", visitorCount = 600_000.0, visitorDate = today),
            fixture(code = "11710", visitorCount = 600_000.0, visitorDate = today.minusDays(30)),
        )

        val regions = service.recommendedRegions(query(), DomainLocale.KO).regions.associateBy { it.code }

        val fresh = regions.getValue("11680").score(RegionScoreFactor.VISITOR_VOLUME).score.value
        val stale = regions.getValue("11710").score(RegionScoreFactor.VISITOR_VOLUME).score.value
        assertEquals(fresh / 2.0, stale, 1e-9)
    }

    @Test
    fun `7일까지는 감쇠하지 않는다 — 정상 주기가 벌을 받으면 안 된다`() {
        val service = service(
            fixture(code = "11680", visitorCount = 600_000.0, visitorDate = today),
            fixture(code = "11710", visitorCount = 600_000.0, visitorDate = today.minusDays(7)),
        )

        val regions = service.recommendedRegions(query(), DomainLocale.KO).regions.associateBy { it.code }

        assertEquals(
            regions.getValue("11680").score(RegionScoreFactor.VISITOR_VOLUME).score.value,
            regions.getValue("11710").score(RegionScoreFactor.VISITOR_VOLUME).score.value,
            1e-9,
        )
    }

    @Test
    fun `감쇠는 방문자수에만 걸린다 — 월 지표는 오래돼도 그대로다`() {
        // 체류 강도의 기준일이 2개월 전(월 단위라 정상)인데도 만점이 유지되어야 한다.
        val service = service(
            fixture(code = "11680", visitorCount = 600_000.0, visitorDate = today, stayIntensity = 70.0),
            fixture(code = "11710", visitorCount = 400_000.0, visitorDate = today, stayIntensity = 30.0),
        )

        val regions = service.recommendedRegions(query(), DomainLocale.KO).regions.associateBy { it.code }

        // 정규화 최댓값 = 100점. 감쇠가 걸렸다면 71일 경과라 절반 이하로 떨어진다.
        assertEquals(100.0, regions.getValue("11680").score(RegionScoreFactor.STAY_INTENSITY).score.value, 1e-9)
    }

    @Test
    fun `기준일을 모르는 방문자수는 감쇠하지 않는다 — 나이를 모르면 깎을 근거가 없다`() {
        val service = service(
            fixture(code = "11680", visitorCount = 600_000.0, visitorDate = today),
            fixture(code = "11710", visitorCount = 600_000.0, visitorDate = null),
        )

        val regions = service.recommendedRegions(query(), DomainLocale.KO).regions.associateBy { it.code }

        val unknown = regions.getValue("11710").score(RegionScoreFactor.VISITOR_VOLUME)
        assertNull(unknown.basisAt)
        assertEquals(
            regions.getValue("11680").score(RegionScoreFactor.VISITOR_VOLUME).score.value,
            unknown.score.value,
            1e-9,
        )
    }

    @Test
    fun `결측 방문자수는 항목 자체가 빠진다 — 0점으로 채우지 않는다`() {
        val service = service(
            fixture(code = "11680", visitorCount = null, visitorDate = null),
        )

        val region = service.recommendedRegions(query(), DomainLocale.KO).regions.single()

        assertTrue(region.scores.none { it.score.factor == RegionScoreFactor.VISITOR_VOLUME })
        assertEquals(region.scores.size, region.scoreFactorCount)
        assertNotNull(region.scores.firstOrNull { it.score.factor == RegionScoreFactor.CONGESTION })
    }

    @Test
    fun `낡은 방문자수는 총점 순위를 실제로 내린다`() {
        // 감쇠를 가중치가 아니라 점수에 곱하는 이유다. 가중치를 깎으면 분모도 함께 줄어
        // 총점이 거의 그대로이고, 낡은 데이터가 대가 없이 통과한다.
        val service = service(
            fixture(code = "11680", visitorCount = 600_000.0, visitorDate = today.minusDays(60)),
            fixture(code = "11710", visitorCount = 600_000.0, visitorDate = today),
        )

        val regions = service.recommendedRegions(query(), DomainLocale.KO).regions

        assertEquals("11710", regions.first().code, "신선한 쪽이 앞에 와야 한다")
        assertTrue(regions.first().totalScore > regions.last().totalScore)
    }

    @Test
    fun `나이가 실려 와도 방문자수가 아닌 항목은 감쇠하지 않는다`() {
        // 지금은 어댑터가 방문자수에만 나이를 싣지만, 그 규율이 느슨해져도 감쇠 대상은
        // 코드가 정한다. 체류 강도에 60일 나이를 억지로 실어도 만점이 유지되어야 한다.
        // 지역이 둘이어야 정규화 구간이 생겨 상위 지역이 만점이 된다 — 한 지역만 두면 구간
        // 폭이 0이라 모든 항목이 중립 50점이 되어 감쇠 여부를 구분할 수 없다.
        fun staleFixture(code: String, stay: Double) = RegionFixture(
            region = RegionRow(
                code = code,
                name = AreaName(ko = "강남구", en = "Gangnam-gu", ja = null, zh = null),
                provinceName = AreaName(ko = "서울특별시", en = "Seoul", ja = null, zh = null),
                center = Coordinates(latitude = 37.5, longitude = 127.05),
            ),
            metrics = mapOf(
                RegionScoreFactor.CONGESTION to RegionMetric(50.0, today, ageDays = 60),
                RegionScoreFactor.STAY_INTENSITY to
                    RegionMetric(stay, LocalDate.of(2026, 6, 1), ageDays = 60),
            ),
        )

        val regions = service(staleFixture("11680", stay = 70.0), staleFixture("11710", stay = 30.0))
            .recommendedRegions(query(), DomainLocale.KO)
            .regions
            .associateBy { it.code }

        // 60일 나이가 실려 있어도 월 지표는 만점 그대로다. 감쇠가 걸리면 절반 이하가 된다.
        assertEquals(100.0, regions.getValue("11680").score(RegionScoreFactor.STAY_INTENSITY).score.value, 1e-9)
    }

    @Test
    fun `지표는 코드가 맞는 지역에만 붙는다`() {
        // 질의가 나뉘면서 결합이 코드로 이뤄진다 — 어긋나면 남의 지역 점수가 실린다.
        val service = service(
            fixture(code = "11680", visitorCount = 600_000.0, visitorDate = today),
            fixture(code = "11710", visitorCount = 300_000.0, visitorDate = today),
        )

        val regions = service.recommendedRegions(query(), DomainLocale.KO).regions.associateBy { it.code }

        // 방문자수가 큰 쪽이 만점, 작은 쪽이 0점이다(정규화 양 끝).
        assertEquals(100.0, regions.getValue("11680").score(RegionScoreFactor.VISITOR_VOLUME).score.value, 1e-9)
        assertEquals(0.0, regions.getValue("11710").score(RegionScoreFactor.VISITOR_VOLUME).score.value, 1e-9)
    }
}
