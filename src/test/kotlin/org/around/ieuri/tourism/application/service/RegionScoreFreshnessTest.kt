package org.around.ieuri.tourism.application.service

import org.around.ieuri.tourism.application.policy.RegionRecommendationPolicy
import org.around.ieuri.tourism.application.query.OriginDistrict
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 신선도 감쇠(SPEC-0015).
 *
 * 감쇠는 **방문자수에만** 걸린다 — 다른 항목에 같은 감쇠를 걸면 월 지표가 정상 주기만으로
 * 반값이 된다.
 */
class RegionScoreFreshnessTest {

    private val policy = RegionRecommendationPolicy(
        weights = RegionScoreWeights.uniform(),
        distanceScoring = DistanceScoring(30_000.0),
        freshnessDecay = FreshnessDecay(plateauDays = 7, halfLifeDays = 23.0),
        minFactorCount = 2,
        // 관심사는 감쇠지 후보 선별이 아니다 — 시도 규모 필터를 무력화한다
        // (선별은 RegionCandidateSelectionTest가 본다).
        minDistrictsPerProvince = 1,
        defaultLimit = 10,
        maxLimit = 50,
    )

    /**
     * 경계 판정 결과를 서울(11)로 고정하되 그 시군구 코드는 픽스처에 없는 값으로 둬
     * 자기 지역 제외가 픽스처를 건드리지 않게 한다.
     */
    private fun service(vararg fixtures: RegionFixture) = RegionRecommendationService(
        RegionMetricQueryPortStub(
            fixtures.toList(),
            OriginDistrict(code = "11999", provinceCode = "11"),
        ),
        policy,
    )

    private fun fixture(
        code: String,
        visitorCount: Double?,
        visitorAgeDays: Long?,
        concentrationRate: Double? = 50.0,
        stayIntensity: Double? = 70.0,
    ) = RegionFixture(
        region = RegionRow(
            code = code,
            provinceCode = "11",
            name = AreaName(ko = "강남구", en = "Gangnam-gu", ja = null, zh = null),
            provinceName = AreaName(ko = "서울특별시", en = "Seoul", ja = null, zh = null),
            center = Coordinates(latitude = 37.5, longitude = 127.05),
        ),
        metrics = buildMap {
            concentrationRate?.let {
                put(RegionScoreFactor.CONGESTION, RegionMetric(it))
            }
            visitorCount?.let {
                put(RegionScoreFactor.VISITOR_VOLUME, RegionMetric(it, ageDays = visitorAgeDays))
            }
            stayIntensity?.let {
                put(RegionScoreFactor.STAY_INTENSITY, RegionMetric(it))
            }
        },
    )

    /**
     * 기본 좌표는 픽스처의 대표 좌표와 같다 — 지역이 전부 같은 좌표라 거리 점수가 동일해지고,
     * 총점 비교에 남는 차이는 오직 감쇠에서 온다.
     */
    private fun query(origin: Coordinates = Coordinates(latitude = 37.5, longitude = 127.05)) =
        RecommendedRegionQuery(origin = origin, limit = 10)

    private fun RecommendedRegionItemView.score(factor: RegionScoreFactor) =
        scores.single { it.score.factor == factor }

    @Test
    fun `30일 지난 방문자수는 점수가 절반이 된다`() {
        // 두 지역의 원본 방문자수가 같고 나이만 다르다 — 정규화 후 점수는 둘 다 만점이므로
        // 차이는 오직 감쇠에서 온다.
        val service = service(
            fixture(code = "11680", visitorCount = 600_000.0, visitorAgeDays = 0),
            fixture(code = "11710", visitorCount = 600_000.0, visitorAgeDays = 30),
        )

        val regions = service.recommendedRegions(query(), DomainLocale.KO).regions.associateBy { it.code }

        val fresh = regions.getValue("11680").score(RegionScoreFactor.VISITOR_VOLUME).score.value
        val stale = regions.getValue("11710").score(RegionScoreFactor.VISITOR_VOLUME).score.value
        assertEquals(fresh / 2.0, stale, 1e-9)
    }

    @Test
    fun `7일까지는 감쇠하지 않는다 — 정상 주기가 벌을 받으면 안 된다`() {
        val service = service(
            fixture(code = "11680", visitorCount = 600_000.0, visitorAgeDays = 0),
            fixture(code = "11710", visitorCount = 600_000.0, visitorAgeDays = 7),
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
        val service = service(
            fixture(code = "11680", visitorCount = 600_000.0, visitorAgeDays = 0, stayIntensity = 70.0),
            fixture(code = "11710", visitorCount = 400_000.0, visitorAgeDays = 0, stayIntensity = 30.0),
        )

        val regions = service.recommendedRegions(query(), DomainLocale.KO).regions.associateBy { it.code }

        // 정규화 최댓값 = 100점. 감쇠가 걸렸다면 절반 이하로 떨어진다.
        assertEquals(100.0, regions.getValue("11680").score(RegionScoreFactor.STAY_INTENSITY).score.value, 1e-9)
    }

    @Test
    fun `나이를 모르는 방문자수는 감쇠하지 않는다 — 깎을 근거가 없다`() {
        val service = service(
            fixture(code = "11680", visitorCount = 600_000.0, visitorAgeDays = 0),
            fixture(code = "11710", visitorCount = 600_000.0, visitorAgeDays = null),
        )

        val regions = service.recommendedRegions(query(), DomainLocale.KO).regions.associateBy { it.code }

        assertEquals(
            regions.getValue("11680").score(RegionScoreFactor.VISITOR_VOLUME).score.value,
            regions.getValue("11710").score(RegionScoreFactor.VISITOR_VOLUME).score.value,
            1e-9,
        )
    }

    @Test
    fun `결측 방문자수는 항목 자체가 빠진다 — 0점으로 채우지 않는다`() {
        val service = service(
            fixture(code = "11680", visitorCount = null, visitorAgeDays = null),
        )

        val region = service.recommendedRegions(query(), DomainLocale.KO).regions.single()

        assertTrue(region.scores.none { it.score.factor == RegionScoreFactor.VISITOR_VOLUME })
        assertEquals(region.scores.size, region.scoreFactorCount)
        assertNotNull(region.scores.firstOrNull { it.score.factor == RegionScoreFactor.CONGESTION })
    }

    @Test
    fun `낡은 방문자수는 총점 순위를 실제로 내린다`() {
        // 감쇠를 가중치가 아니라 점수에 곱하는 이유다. 가중치를 깎으면 분모도 함께 줄어
        // 총점이 거의 그대로다. 총점은 계약에 없으므로 목록 순서와 항목 순위로 확인한다.
        val service = service(
            fixture(code = "11680", visitorCount = 600_000.0, visitorAgeDays = 60),
            fixture(code = "11710", visitorCount = 600_000.0, visitorAgeDays = 0),
        )

        val regions = service.recommendedRegions(query(), DomainLocale.KO).regions

        assertEquals("11710", regions.first().code, "신선한 쪽이 앞에 와야 한다")
        assertEquals(1, regions.first().score(RegionScoreFactor.VISITOR_VOLUME).rank)
        assertEquals(2, regions.last().score(RegionScoreFactor.VISITOR_VOLUME).rank)
    }

    @Test
    fun `나이가 실려 와도 방문자수가 아닌 항목은 감쇠하지 않는다`() {
        // 어댑터가 방문자수에만 나이를 싣지만, 그 규율이 느슨해져도 감쇠 대상은 코드가 정한다.
        // 지역이 둘이어야 정규화 구간이 생겨 상위 지역이 만점이 된다 — 한 지역만 두면 구간
        // 폭이 0이라 모든 항목이 중립 50점이 되어 감쇠 여부를 구분할 수 없다.
        fun staleFixture(code: String, stay: Double) = RegionFixture(
            region = RegionRow(
                code = code,
                provinceCode = "11",
                name = AreaName(ko = "강남구", en = "Gangnam-gu", ja = null, zh = null),
                provinceName = AreaName(ko = "서울특별시", en = "Seoul", ja = null, zh = null),
                center = Coordinates(latitude = 37.5, longitude = 127.05),
            ),
            metrics = mapOf(
                RegionScoreFactor.CONGESTION to RegionMetric(50.0, ageDays = 60),
                RegionScoreFactor.STAY_INTENSITY to RegionMetric(stay, ageDays = 60),
            ),
        )

        val regions = service(staleFixture("11680", stay = 70.0), staleFixture("11710", stay = 30.0))
            .recommendedRegions(query(), DomainLocale.KO)
            .regions
            .associateBy { it.code }

        assertEquals(100.0, regions.getValue("11680").score(RegionScoreFactor.STAY_INTENSITY).score.value, 1e-9)
    }

    @Test
    fun `지표는 코드가 맞는 지역에만 붙는다`() {
        // 질의가 나뉘면서 결합이 코드로 이뤄진다 — 어긋나면 남의 지역 점수가 실린다.
        val service = service(
            fixture(code = "11680", visitorCount = 600_000.0, visitorAgeDays = 0),
            fixture(code = "11710", visitorCount = 300_000.0, visitorAgeDays = 0),
        )

        val regions = service.recommendedRegions(query(), DomainLocale.KO).regions.associateBy { it.code }

        // 방문자수가 큰 쪽이 만점, 작은 쪽이 0점이다(정규화 양 끝).
        assertEquals(100.0, regions.getValue("11680").score(RegionScoreFactor.VISITOR_VOLUME).score.value, 1e-9)
        assertEquals(0.0, regions.getValue("11710").score(RegionScoreFactor.VISITOR_VOLUME).score.value, 1e-9)
    }
}
