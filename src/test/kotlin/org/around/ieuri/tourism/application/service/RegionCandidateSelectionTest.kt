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
 * 추천 후보 선별(SPEC-0015).
 *
 * 추천은 "여기 말고 **근처** 저기"다. 그래서 자기 지역·다른 시도·선택지가 없는 시도는 애초에
 * 후보가 아니며, 산출 항목이 최소 개수에 못 미치는 지역도 빠진다.
 *
 * origin이 없거나 어느 시군구에도 걸리지 않으면(바다·해외) **전국 대상으로 되돌아간다** —
 * 오류가 아니다. 그때도 시도 규모 필터는 그대로 걸린다.
 */
class RegionCandidateSelectionTest {

    private val today = LocalDate.of(2026, 8, 11)

    private fun policy(minFactorCount: Int = 4, minDistrictsPerProvince: Int = 3) =
        RegionRecommendationPolicy(
            weights = RegionScoreWeights.uniform(),
            distanceScoring = DistanceScoring(30_000.0),
            freshnessDecay = FreshnessDecay(plateauDays = 7, halfLifeDays = 23.0),
            minFactorCount = minFactorCount,
            minDistrictsPerProvince = minDistrictsPerProvince,
            defaultLimit = 10,
            maxLimit = 50,
        )

    private fun service(
        fixtures: List<RegionFixture>,
        originDistrict: OriginDistrict? = null,
        policy: RegionRecommendationPolicy = policy(),
    ) = RegionRecommendationService(RegionMetricQueryPortStub(fixtures, originDistrict), policy)

    /** 지표 4종이 모두 있는 후보 — 최소 항목 수(4)를 넘겨 선별 규칙만 남긴다. */
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

    private fun query(origin: Coordinates? = null) =
        RecommendedRegionQuery(origin = origin, limit = 10)

    private fun RegionRecommendationService.codes(origin: Coordinates? = null): List<String> =
        recommendedRegions(query(origin), DomainLocale.KO).regions.map { it.code }

    @Test
    fun `origin이 속한 시군구는 결과에 오지 않는다 — 현재 위치 확인이 아니라 추천이다`() {
        val service = service(
            fixtures = listOf(fixture("11680"), fixture("11110"), fixture("11170")),
            // 강남 좌표가 강남구(11680)에 걸린 상태.
            originDistrict = OriginDistrict(code = "11680", provinceCode = "11"),
        )

        val codes = service.codes(origin = GANGNAM)

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

        val codes = service.codes(origin = GANGNAM)

        assertTrue(codes.all { it.startsWith("11") }, "서울 밖이 섞였다: $codes")
        assertEquals(listOf("11110", "11170"), codes.sorted())
    }

    @Test
    fun `origin이 없으면 전국에서 고른다 — 시도를 좁힐 근거가 없다`() {
        val service = service(
            fixtures = listOf(
                fixture("11680"), fixture("11110"), fixture("11170"),
                fixture("41110"), fixture("41130"), fixture("41190"),
            ),
            originDistrict = OriginDistrict(code = "11680", provinceCode = "11"),
        )

        // origin이 null이면 경계 판정 자체를 하지 않으므로 스텁의 값이 있어도 전국이다.
        val codes = service.codes(origin = null)

        assertEquals(
            listOf("11110", "11170", "11680", "41110", "41130", "41190"),
            codes.sorted(),
        )
    }

    @Test
    fun `어느 시군구에도 걸리지 않는 좌표는 전국 대상이다 — 오류가 아니다`() {
        val service = service(
            fixtures = listOf(
                fixture("11680"), fixture("11110"), fixture("11170"),
                fixture("41110"), fixture("41130"), fixture("41190"),
            ),
            // 바다·해외 — 경계 안에 있는 지역이 없다.
            originDistrict = null,
        )

        val codes = service.codes(origin = Coordinates(latitude = 35.0, longitude = 129.9))

        assertEquals(
            listOf("11110", "11170", "11680", "41110", "41130", "41190"),
            codes.sorted(),
        )
    }

    @Test
    fun `시군구가 최소 개수 미만인 시도는 통째로 빠진다 — 고를 대상이 없다`() {
        val service = service(
            // 서울 3건(통과) + 세종 1건(36) + 제주 2건(50) — 뒤 둘은 3 미만이다.
            fixtures = listOf(
                fixture("11680"), fixture("11110"), fixture("11170"),
                fixture("36110"),
                fixture("50110"), fixture("50130"),
            ),
        )

        val codes = service.codes(origin = null)

        assertEquals(listOf("11110", "11170", "11680"), codes.sorted())
    }

    @Test
    fun `소규모 시도 안에서 조회하면 빈 목록이다 — 자기 시도가 통째로 빠졌기 때문이다`() {
        val service = service(
            fixtures = listOf(
                fixture("11680"), fixture("11110"), fixture("11170"),
                fixture("50110"), fixture("50130"),
            ),
            // 제주시에서 조회 — 같은 시도(50)로 좁히는데 그 시도가 이미 제외됐다.
            originDistrict = OriginDistrict(code = "50110", provinceCode = "50"),
        )

        val codes = service.codes(origin = Coordinates(latitude = 33.5, longitude = 126.53))

        assertTrue(codes.isEmpty(), "소규모 시도는 서울로 넘어가지 않고 빈 목록이어야 한다: $codes")
    }

    @Test
    fun `항목이 3개뿐인 지역은 추천되지 않는다 — 최소 항목 수가 4다`() {
        // 화성시가 체류·소비 강도 결측(3항목)으로 7위에 오던 사례다. 결측 지역의 원본 값을
        // 더 높게 줘 "점수가 낮아서 밀린 것"이 아님을 분명히 한다.
        val service = service(
            fixtures = listOf(
                fixture("41590", factors = THREE_FACTORS, value = 100.0),
                fixture("41110"), fixture("41130"), fixture("41190"),
            ),
        )

        val codes = service.codes(origin = null)

        assertFalse("41590" in codes, "항목 3개짜리가 남았다")
        assertEquals(listOf("41110", "41130", "41190"), codes.sorted())
    }

    @Test
    fun `최소 항목 수를 3으로 낮추면 같은 지역이 다시 추천된다`() {
        // 앞 테스트의 탈락이 결측 자체가 아니라 설정값에서 온 것임을 고정한다.
        val service = service(
            fixtures = listOf(
                fixture("41590", factors = THREE_FACTORS, value = 100.0),
                fixture("41110"), fixture("41130"), fixture("41190"),
            ),
            policy = policy(minFactorCount = 3),
        )

        assertTrue("41590" in service.codes(origin = null))
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

        val regions = service.recommendedRegions(query(GANGNAM), DomainLocale.KO).regions

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
