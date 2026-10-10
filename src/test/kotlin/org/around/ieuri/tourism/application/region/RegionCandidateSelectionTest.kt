package org.around.ieuri.tourism.application.region

import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.common.event.LocationAccessed
import org.around.ieuri.tourism.application.region.provided.dto.RecommendedRegionQuery
import org.around.ieuri.tourism.application.region.required.RegionEventPublisherPort
import org.around.ieuri.tourism.application.region.required.dto.DistrictRegion
import org.around.ieuri.tourism.application.region.required.dto.RegionRow
import org.around.ieuri.tourism.domain.region.DistanceScoring
import org.around.ieuri.tourism.domain.region.FreshnessDecay
import org.around.ieuri.tourism.domain.region.RegionMetric
import org.around.ieuri.tourism.domain.region.RegionScoreFactor
import org.around.ieuri.tourism.domain.region.RegionScoreWeights
import org.around.ieuri.tourism.domain.region.RegionScoring
import org.around.ieuri.tourism.domain.region.spec.ProvinceEligibility
import org.around.ieuri.tourism.domain.shared.AreaName
import org.around.ieuri.tourism.domain.shared.Coordinates
import org.around.ieuri.tourism.domain.shared.DomainLocale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 추천 후보 선별(SPEC-0015 R21·R30). 같은 시도로 좁히는 일은 질의가 한다.
 * - origin이 어느 시군구에도 걸리지 않거나 시도의 선택지가 최소 개수 미만이면 빈 목록이다.
 */
class RegionCandidateSelectionTest {

    /** 기본 최소 항목 수 5 = 지표 4종 + 거리. */
    private fun policy(minFactorCount: Int = 5, minDistrictsPerProvince: Int = 3) = RegionRecommendationPolicy(
        scoring = RegionScoring(RegionScoreWeights.uniform(), DistanceScoring(30_000.0), FreshnessDecay(plateauDays = 7, halfLifeDays = 23.0), minFactorCount),
        provinceEligibility = ProvinceEligibility(minDistrictsPerProvince),
        defaultLimit = 10,
        maxLimit = 50,
    )

    private fun stub(fixtures: List<RegionFixture>, originDistrict: DistrictRegion?) = RegionMetricQueryPortStub(fixtures, originDistrict)

    private fun service(
        fixtures: List<RegionFixture>,
        originDistrict: DistrictRegion? = null,
        policy: RegionRecommendationPolicy = policy(),
    ) = RegionRecommendationService(stub(fixtures, originDistrict), policy, mockk<RegionEventPublisherPort>(relaxed = true))

    /** 지표 4종이 모두 있는 후보 — 거리까지 5항목이라 최소 항목 수를 넘겨 선별 규칙만 남긴다. */
    private fun fixture(
        code: String,
        provinceCode: String = code.take(2),
        factors: Set<RegionScoreFactor> = FULL_FACTORS,
        value: Double = 50.0,
    ) = RegionFixture(
        region = RegionRow(
            code = code,
            provinceCode = provinceCode,
            name = AreaName(ko = "테스트구$code", en = "District $code", ja = null, zh = null),
            provinceName = AreaName(ko = "시도$provinceCode", en = "Province $provinceCode", ja = null, zh = null),
            center = Coordinates(latitude = 37.5, longitude = 127.05),
        ),
        metrics = factors.associateWith { RegionMetric(value) },
    )

    private fun query(origin: Coordinates = GANGNAM) = RecommendedRegionQuery(origin = origin, limit = 10)

    private fun RegionRecommendationService.codes(origin: Coordinates = GANGNAM): List<String> = recommendedRegions(query(origin), DomainLocale.KO, memberId = null).regions.map { it.code }

    @Test
    fun `origin이 속한 시군구는 결과에 오지 않는다 — 현재 위치 확인이 아니라 추천이다`() {
        val service = service(
            fixtures = listOf(fixture("11680"), fixture("11110"), fixture("11170")),
            // 강남 좌표가 강남구(11680)에 걸린 상태.
            originDistrict = DistrictRegion(code = "11680", provinceCode = "11"),
        )

        val codes = service.codes()

        assertFalse("11680" in codes, "자기 지역이 추천되면 안 된다")
        assertEquals(listOf("11110", "11170"), codes.sorted())
    }

    @Test
    fun `같은 시도의 시군구만 추천된다 — 다른 시도는 근처가 아니다`() {
        val service = service(
            // 서울 3건 + 경기 3건. 경기도 자체는 시도 규모 필터를 통과한다.
            fixtures = listOf(
                fixture("11680"),
                fixture("11110"),
                fixture("11170"),
                fixture("41110"),
                fixture("41130"),
                fixture("41190"),
            ),
            originDistrict = DistrictRegion(code = "11680", provinceCode = "11"),
        )

        val codes = service.codes()

        assertTrue(codes.all { it.startsWith("11") }, "서울 밖이 섞였다: $codes")
        assertEquals(listOf("11110", "11170"), codes.sorted())
    }

    @Test
    fun `다른 시도는 읽지도 않는다 — 걸러내는 것이 아니라 질의가 좁다`() {
        val stub = stub(
            fixtures = listOf(
                fixture("11680"),
                fixture("11110"),
                fixture("11170"),
                fixture("41110"),
                fixture("41130"),
                fixture("41190"),
            ),
            originDistrict = DistrictRegion(code = "11680", provinceCode = "11"),
        )

        RegionRecommendationService(stub, policy(), mockk<RegionEventPublisherPort>(relaxed = true)).recommendedRegions(query(), DomainLocale.KO, memberId = null)

        assertEquals(
            listOf("11"),
            stub.askedProvinceCodes.distinct(),
            "origin의 시도 밖을 읽었다: ${stub.askedProvinceCodes.distinct()}",
        )
        assertEquals(5, stub.askedProvinceCodes.size, "지역 1회 + 지표 4회여야 한다")
    }

    @Test
    fun `어느 시군구에도 걸리지 않는 좌표는 빈 목록이다 — 오류도 전국 폴백도 아니다`() {
        val stub = stub(
            fixtures = listOf(
                fixture("11680"),
                fixture("11110"),
                fixture("11170"),
                fixture("41110"),
                fixture("41130"),
                fixture("41190"),
            ),
            // 바다·해외 — 경계 안에 있는 지역이 없다.
            originDistrict = null,
        )
        val service = RegionRecommendationService(stub, policy(), mockk<RegionEventPublisherPort>(relaxed = true))

        val regions = service
            .recommendedRegions(query(Coordinates(latitude = 35.0, longitude = 129.9)), DomainLocale.KO, memberId = null)
            .regions

        assertTrue(regions.isEmpty(), "전국으로 되돌아가면 안 된다: ${regions.map { it.code }}")
        assertTrue(stub.askedProvinceCodes.isEmpty(), "좁힐 시도를 모르면 지표를 읽을 이유가 없다")
    }

    @Test
    fun `시군구가 최소 개수 미만인 시도에서 조회하면 빈 목록이다 — 고를 대상이 없다`() {
        val service = service(
            // 제주 2건뿐이라 3 미만이다.
            fixtures = listOf(
                fixture("11680"),
                fixture("11110"),
                fixture("11170"),
                fixture("50110"),
                fixture("50130"),
            ),
            originDistrict = DistrictRegion(code = "50110", provinceCode = "50"),
        )

        val codes = service.codes(origin = Coordinates(latitude = 33.5, longitude = 126.53))

        assertTrue(codes.isEmpty(), "소규모 시도는 서울로 넘어가지 않고 빈 목록이어야 한다: $codes")
    }

    @Test
    fun `최소 시군구 수 경계값은 포함된다 — 3개짜리 시도는 남는다`() {
        // 자기 지역을 뺀 수가 아니라 시도 전체 수로 판정한다.
        val service = service(
            fixtures = listOf(fixture("50110"), fixture("50130"), fixture("50150")),
            originDistrict = DistrictRegion(code = "50110", provinceCode = "50"),
        )

        val codes = service.codes(origin = Coordinates(latitude = 33.5, longitude = 126.53))

        assertEquals(listOf("50130", "50150"), codes.sorted())
    }

    @Test
    fun `지표가 3종뿐인 지역은 추천되지 않는다 — 거리를 더해도 최소 항목 수에 못 미친다`() {
        // 결측 지역의 원본 값을 더 높게 줘 점수 탓이 아님을 분명히 한다.
        val service = service(
            fixtures = listOf(
                fixture("41590", factors = THREE_FACTORS, value = 100.0),
                fixture("41110"),
                fixture("41130"),
                fixture("41190"),
            ),
            originDistrict = DistrictRegion(code = "41210", provinceCode = "41"),
        )

        val codes = service.codes()

        assertFalse("41590" in codes, "지표 3종짜리가 남았다")
        assertEquals(listOf("41110", "41130", "41190"), codes.sorted())
    }

    @Test
    fun `최소 항목 수를 4로 낮추면 같은 지역이 다시 추천된다`() {
        // 지표 3종 + 거리 = 4항목. 경계값(== 최소 개수)은 포함된다.
        val service = service(
            fixtures = listOf(
                fixture("41590", factors = THREE_FACTORS, value = 100.0),
                fixture("41110"),
                fixture("41130"),
                fixture("41190"),
            ),
            originDistrict = DistrictRegion(code = "41210", provinceCode = "41"),
            policy = policy(minFactorCount = 4),
        )

        assertTrue("41590" in service.codes())
    }

    @Test
    fun `정규화는 남은 후보 집합에서 이뤄진다 — 걸러진 지역이 척도를 끌지 않는다`() {
        // 다른 시도(41)에 극단적으로 큰 값을 둔다.
        val service = service(
            fixtures = listOf(
                fixture("11680"),
                fixture("11110", value = 10.0),
                fixture("11170", value = 20.0),
                fixture("41110", value = 1_000_000.0),
                fixture("41130"),
                fixture("41190"),
            ),
            originDistrict = DistrictRegion(code = "11680", provinceCode = "11"),
        )

        val regions = service.recommendedRegions(query(), DomainLocale.KO, memberId = null).regions

        // 남은 후보 구간이 10~20이라 체류 강도가 큰 쪽이 만점이다.
        val top = regions.single { it.code == "11170" }
        assertEquals(
            100.0,
            top.scores.single { it.score.factor == RegionScoreFactor.STAY_INTENSITY }.score.value,
            1e-9,
        )
    }

    @Test
    fun `회원의 지역 추천은 좌표 조회 사실을 남기고, 비로그인은 남기지 않는다`() {
        val events = mockk<RegionEventPublisherPort>(relaxed = true)
        val service = RegionRecommendationService(stub(emptyList(), originDistrict = null), policy(), events)

        service.recommendedRegions(query(), DomainLocale.KO, memberId = 7L)
        service.recommendedRegions(query(), DomainLocale.KO, memberId = null)

        verify(exactly = 1) { events.publish(LocationAccessed(7L, "recommendedRegions")) }
        verify(exactly = 1) { events.publish(any()) }
    }

    private companion object {
        /** 강남 좌표. 경계 판정은 스텁이 대신한다. */
        val GANGNAM = Coordinates(latitude = 37.4979, longitude = 127.0276)

        val FULL_FACTORS = setOf(
            RegionScoreFactor.CONGESTION,
            RegionScoreFactor.VISITOR_VOLUME,
            RegionScoreFactor.STAY_INTENSITY,
            RegionScoreFactor.SPENDING_INTENSITY,
        )

        /** 체류·소비 강도 결측. */
        val THREE_FACTORS = setOf(
            RegionScoreFactor.CONGESTION,
            RegionScoreFactor.VISITOR_VOLUME,
            RegionScoreFactor.STAY_INTENSITY,
        )
    }
}
