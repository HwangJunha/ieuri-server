package org.around.ieuri.tourism.application.service

import org.around.ieuri.tourism.application.policy.RegionRecommendationPolicy
import org.around.ieuri.tourism.application.query.OriginDistrict
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
 * 추천 후보 선별(SPEC-0015 R21·R30).
 *
 * 추천은 "여기 말고 **근처** 저기"라 origin이 필수다. 같은 시도로 좁히는 일은 이제 질의가
 * 하고, 서비스에는 자기 지역 제외와 두 개의 빈 목록 가드만 남는다 — origin이 어느 시군구에도
 * 걸리지 않을 때(바다·해외)와 그 시도의 선택지가 최소 개수에 못 미칠 때(세종·제주)다.
 * 어느 쪽도 오류가 아니고 전국으로 되돌아가지도 않는다.
 */
class RegionCandidateSelectionTest {

    private val today = LocalDate.of(2026, 8, 11)

    /**
     * 기본 최소 항목 수가 5인 이유: origin이 필수가 되면서 **거리도 항상 산출되는 한 항목**이라
     * 지표 4종을 갖춘 지역의 항목 수는 5다. 4로 두면 지표 3종짜리(4항목)도 통과해 R30이
     * 아무것도 거르지 않는다.
     */
    private fun policy(minFactorCount: Int = 5, minDistrictsPerProvince: Int = 3) =
        RegionRecommendationPolicy(
            weights = RegionScoreWeights.uniform(),
            distanceScoring = DistanceScoring(30_000.0),
            freshnessDecay = FreshnessDecay(plateauDays = 7, halfLifeDays = 23.0),
            minFactorCount = minFactorCount,
            minDistrictsPerProvince = minDistrictsPerProvince,
            defaultLimit = 10,
            maxLimit = 50,
        )

    private fun stub(fixtures: List<RegionFixture>, originDistrict: OriginDistrict?) =
        RegionMetricQueryPortStub(fixtures, originDistrict)

    private fun service(
        fixtures: List<RegionFixture>,
        originDistrict: OriginDistrict? = null,
        policy: RegionRecommendationPolicy = policy(),
    ) = RegionRecommendationService(stub(fixtures, originDistrict), policy)

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
        metrics = factors.associateWith { RegionMetric(value, today) },
    )

    private fun query(origin: Coordinates = GANGNAM) =
        RecommendedRegionQuery(origin = origin, limit = 10)

    private fun RegionRecommendationService.codes(origin: Coordinates = GANGNAM): List<String> =
        recommendedRegions(query(origin), DomainLocale.KO).regions.map { it.code }

    @Test
    fun `origin이 속한 시군구는 결과에 오지 않는다 — 현재 위치 확인이 아니라 추천이다`() {
        val service = service(
            fixtures = listOf(fixture("11680"), fixture("11110"), fixture("11170")),
            // 강남 좌표가 강남구(11680)에 걸린 상태.
            originDistrict = OriginDistrict(code = "11680", provinceCode = "11"),
        )

        val codes = service.codes()

        assertFalse("11680" in codes, "자기 지역이 추천되면 안 된다")
        assertEquals(listOf("11110", "11170"), codes.sorted())
    }

    @Test
    fun `같은 시도의 시군구만 추천된다 — 다른 시도는 근처가 아니다`() {
        val service = service(
            // 서울 3건 + 경기 3건. 경기도 자체는 시도 규모 필터를 통과하므로,
            // 빠진다면 그것은 오직 "다른 시도"이기 때문이다.
            fixtures = listOf(
                fixture("11680"), fixture("11110"), fixture("11170"),
                fixture("41110"), fixture("41130"), fixture("41190"),
            ),
            originDistrict = OriginDistrict(code = "11680", provinceCode = "11"),
        )

        val codes = service.codes()

        assertTrue(codes.all { it.startsWith("11") }, "서울 밖이 섞였다: $codes")
        assertEquals(listOf("11110", "11170"), codes.sorted())
    }

    @Test
    fun `다른 시도는 읽지도 않는다 — 걸러내는 것이 아니라 질의가 좁다`() {
        // 전국을 읽어 뒤에서 버리는 형태로 되돌아가면 여기가 잡는다(226건 중 202건 폐기).
        val stub = stub(
            fixtures = listOf(
                fixture("11680"), fixture("11110"), fixture("11170"),
                fixture("41110"), fixture("41130"), fixture("41190"),
            ),
            originDistrict = OriginDistrict(code = "11680", provinceCode = "11"),
        )

        RegionRecommendationService(stub, policy()).recommendedRegions(query(), DomainLocale.KO)

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
                fixture("11680"), fixture("11110"), fixture("11170"),
                fixture("41110"), fixture("41130"), fixture("41190"),
            ),
            // 바다·해외 — 경계 안에 있는 지역이 없다.
            originDistrict = null,
        )
        val service = RegionRecommendationService(stub, policy())

        val regions = service
            .recommendedRegions(query(Coordinates(latitude = 35.0, longitude = 129.9)), DomainLocale.KO)
            .regions

        assertTrue(regions.isEmpty(), "전국으로 되돌아가면 안 된다: ${regions.map { it.code }}")
        assertTrue(stub.askedProvinceCodes.isEmpty(), "좁힐 시도를 모르면 지표를 읽을 이유가 없다")
    }

    @Test
    fun `시군구가 최소 개수 미만인 시도에서 조회하면 빈 목록이다 — 고를 대상이 없다`() {
        val service = service(
            // 제주 2건(50)뿐이라 3 미만이다. 서울 3건은 다른 시도라 애초에 읽히지도 않는다.
            fixtures = listOf(
                fixture("11680"), fixture("11110"), fixture("11170"),
                fixture("50110"), fixture("50130"),
            ),
            originDistrict = OriginDistrict(code = "50110", provinceCode = "50"),
        )

        val codes = service.codes(origin = Coordinates(latitude = 33.5, longitude = 126.53))

        assertTrue(codes.isEmpty(), "소규모 시도는 서울로 넘어가지 않고 빈 목록이어야 한다: $codes")
    }

    @Test
    fun `최소 시군구 수 경계값은 포함된다 — 3개짜리 시도는 남는다`() {
        // 앞 테스트의 빈 목록이 "시도가 작아서"이지 "제주라서"가 아님을 고정한다. 자기 지역을
        // 뺀 2건이 아니라 시도 전체 3건으로 판정하는 것도 여기서 잡힌다.
        val service = service(
            fixtures = listOf(fixture("50110"), fixture("50130"), fixture("50150")),
            originDistrict = OriginDistrict(code = "50110", provinceCode = "50"),
        )

        val codes = service.codes(origin = Coordinates(latitude = 33.5, longitude = 126.53))

        assertEquals(listOf("50130", "50150"), codes.sorted())
    }

    @Test
    fun `지표가 3종뿐인 지역은 추천되지 않는다 — 거리를 더해도 최소 항목 수에 못 미친다`() {
        // 화성시가 체류·소비 강도 결측으로 7위에 오던 사례다. 결측 지역의 원본 값을 더 높게
        // 줘 "점수가 낮아서 밀린 것"이 아님을 분명히 한다.
        val service = service(
            fixtures = listOf(
                fixture("41590", factors = THREE_FACTORS, value = 100.0),
                fixture("41110"), fixture("41130"), fixture("41190"),
            ),
            originDistrict = OriginDistrict(code = "41210", provinceCode = "41"),
        )

        val codes = service.codes()

        assertFalse("41590" in codes, "지표 3종짜리가 남았다")
        assertEquals(listOf("41110", "41130", "41190"), codes.sorted())
    }

    @Test
    fun `최소 항목 수를 4로 낮추면 같은 지역이 다시 추천된다`() {
        // 앞 테스트의 탈락이 결측 자체가 아니라 설정값에서 온 것임을 고정한다. 지표 3종에
        // 거리가 붙어 4항목이므로 경계값(== 최소 개수)이 포함된다는 것도 함께 잡힌다.
        val service = service(
            fixtures = listOf(
                fixture("41590", factors = THREE_FACTORS, value = 100.0),
                fixture("41110"), fixture("41130"), fixture("41190"),
            ),
            originDistrict = OriginDistrict(code = "41210", provinceCode = "41"),
            policy = policy(minFactorCount = 4),
        )

        assertTrue("41590" in service.codes())
    }

    @Test
    fun `정규화는 남은 후보 집합에서 이뤄진다 — 걸러진 지역이 척도를 끌지 않는다`() {
        // 다른 시도(41)에 극단적으로 큰 값을 둔다. 전국 집합에서 척도를 뽑으면 서울 지역들이
        // 좁은 구간에 눌려 만점이 나오지 않는다.
        val service = service(
            fixtures = listOf(
                fixture("11680"), fixture("11110", value = 10.0), fixture("11170", value = 20.0),
                fixture("41110", value = 1_000_000.0), fixture("41130"), fixture("41190"),
            ),
            originDistrict = OriginDistrict(code = "11680", provinceCode = "11"),
        )

        val regions = service.recommendedRegions(query(), DomainLocale.KO).regions

        // 남은 후보는 11110(10.0)과 11170(20.0)뿐이라 구간이 10~20이 되고, 그 안에서
        // 체류 강도가 큰 쪽이 만점이다. 41110의 100만이 척도에 섞였다면 둘 다 0점에 가깝다.
        val top = regions.single { it.code == "11170" }
        assertEquals(
            100.0,
            top.scores.single { it.score.factor == RegionScoreFactor.STAY_INTENSITY }.score.value,
            1e-9,
        )
    }

    private companion object {
        /** 강남 실측 좌표. 경계 판정은 스텁이 대신하므로 값 자체는 표식일 뿐이다. */
        val GANGNAM = Coordinates(latitude = 37.4979, longitude = 127.0276)

        val FULL_FACTORS = setOf(
            RegionScoreFactor.CONGESTION,
            RegionScoreFactor.VISITOR_VOLUME,
            RegionScoreFactor.STAY_INTENSITY,
            RegionScoreFactor.SPENDING_INTENSITY,
        )

        /** 체류·소비 강도 결측 — 실측에서 화성시가 이 모양이었다. */
        val THREE_FACTORS = setOf(
            RegionScoreFactor.CONGESTION,
            RegionScoreFactor.VISITOR_VOLUME,
            RegionScoreFactor.STAY_INTENSITY,
        )
    }
}
