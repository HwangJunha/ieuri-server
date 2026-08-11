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
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 항목별 순위(SPEC-0015 · 계약의 `RegionScore.rank`).
 *
 * 순위는 **상한을 적용하기 전의 후보 전체**에서 나오고 1이 가장 좋다. 반환된 목록 안에서
 * 다시 매기면 요청 개수에 따라 같은 지역의 순위가 달라져, 클라이언트가 "이 시도에서 몇 위"
 * 라고 말할 수 없게 된다.
 */
class RegionScoreRankTest {

    private val today = LocalDate.of(2026, 8, 11)

    /** 관심사는 순위뿐이라 최소 항목 수·시도 규모 필터를 무력화한다. */
    private fun policy(minFactorCount: Int = 1) = RegionRecommendationPolicy(
        weights = RegionScoreWeights.uniform(),
        distanceScoring = DistanceScoring(30_000.0),
        freshnessDecay = FreshnessDecay(plateauDays = 7, halfLifeDays = 23.0),
        minFactorCount = minFactorCount,
        minDistrictsPerProvince = 1,
        defaultLimit = 10,
        maxLimit = 50,
    )

    /**
     * origin의 시군구 코드는 픽스처에 없는 값이다 — 자기 지역 제외가 후보를 건드리지 않게 해
     * 모수가 픽스처 전건임을 분명히 한다.
     */
    private fun service(
        fixtures: List<RegionFixture>,
        policy: RegionRecommendationPolicy = policy(),
    ) = RegionRecommendationService(
        RegionMetricQueryPortStub(fixtures, OriginDistrict(code = "11999", provinceCode = "11")),
        policy,
    )

    /**
     * 모든 지역이 같은 좌표라 거리 점수가 동일해진다 — 순위 차이는 오직 지정한 지표에서 온다.
     */
    private fun fixture(
        code: String,
        metrics: Map<RegionScoreFactor, Double>,
    ) = RegionFixture(
        region = RegionRow(
            code = code,
            provinceCode = "11",
            name = AreaName(ko = "테스트구$code", en = "District $code", ja = null, zh = null),
            provinceName = AreaName(ko = "서울특별시", en = "Seoul", ja = null, zh = null),
            center = Coordinates(latitude = 37.5, longitude = 127.05),
        ),
        metrics = metrics.mapValues { (_, value) -> RegionMetric(value, today) },
    )

    private fun query(limit: Int = 10) =
        RecommendedRegionQuery(origin = Coordinates(latitude = 37.5, longitude = 127.05), limit = limit)

    private fun RecommendedRegionItemView.rankOf(factor: RegionScoreFactor) =
        scores.single { it.score.factor == factor }.rank

    private fun RecommendedRegionItemView.valueOf(factor: RegionScoreFactor) =
        scores.single { it.score.factor == factor }.score.value

    @Test
    fun `순위 1위는 항목 값이 가장 높은 지역이다`() {
        val service = service(
            listOf(
                fixture("11110", mapOf(RegionScoreFactor.STAY_INTENSITY to 10.0)),
                fixture("11170", mapOf(RegionScoreFactor.STAY_INTENSITY to 90.0)),
                fixture("11200", mapOf(RegionScoreFactor.STAY_INTENSITY to 50.0)),
            ),
        )

        val regions = service.recommendedRegions(query(), DomainLocale.KO).regions.associateBy { it.code }

        assertEquals(1, regions.getValue("11170").rankOf(RegionScoreFactor.STAY_INTENSITY))
        assertEquals(2, regions.getValue("11200").rankOf(RegionScoreFactor.STAY_INTENSITY))
        assertEquals(3, regions.getValue("11110").rankOf(RegionScoreFactor.STAY_INTENSITY))
    }

    @Test
    fun `혼잡도 1위는 가장 덜 붐비는 지역이다 — 원본 값이 가장 낮은 쪽이다`() {
        // 혼잡도는 정규화가 방향을 뒤집으므로, 값 내림차순 하나로 매겨도 원본 최솟값이 1위다.
        // 뒤집기를 순위에서 한 번 더 하거나 빠뜨리면 여기가 잡는다.
        val service = service(
            listOf(
                fixture("11110", mapOf(RegionScoreFactor.CONGESTION to 90.0)), // 가장 붐빔
                fixture("11170", mapOf(RegionScoreFactor.CONGESTION to 10.0)), // 가장 한산
                fixture("11200", mapOf(RegionScoreFactor.CONGESTION to 50.0)),
            ),
        )

        val regions = service.recommendedRegions(query(), DomainLocale.KO).regions.associateBy { it.code }

        assertEquals(1, regions.getValue("11170").rankOf(RegionScoreFactor.CONGESTION))
        assertEquals(2, regions.getValue("11200").rankOf(RegionScoreFactor.CONGESTION))
        assertEquals(3, regions.getValue("11110").rankOf(RegionScoreFactor.CONGESTION))
    }

    @Test
    fun `순위는 잘리기 전 후보 전체에서 매긴다 — 반환된 3건에 4위 이상이 올 수 있다`() {
        // 체류 강도는 코드 순서대로 낮아지고, 소비 강도는 그 반대다. 총점이 같아 정렬은
        // 코드 오름차순이 되어 앞의 3건만 반환되는데, 그 3건의 소비 강도 순위는 6·5·4다.
        // 순위를 반환 목록에서 다시 매기면 1·2·3이 나와 여기가 실패한다.
        val service = service(
            (0 until 6).map { index ->
                fixture(
                    code = "1110$index",
                    metrics = mapOf(
                        RegionScoreFactor.STAY_INTENSITY to (60 - index * 10).toDouble(),
                        RegionScoreFactor.SPENDING_INTENSITY to (10 + index * 10).toDouble(),
                    ),
                )
            },
        )

        val regions = service.recommendedRegions(query(limit = 3), DomainLocale.KO).regions

        assertEquals(listOf("11100", "11101", "11102"), regions.map { it.code })
        assertEquals(listOf(1, 2, 3), regions.map { it.rankOf(RegionScoreFactor.STAY_INTENSITY) })
        assertEquals(
            listOf(6, 5, 4),
            regions.map { it.rankOf(RegionScoreFactor.SPENDING_INTENSITY) },
            "잘린 뒤에 순위를 매기면 1·2·3이 된다",
        )
        assertTrue(
            regions.any { it.rankOf(RegionScoreFactor.SPENDING_INTENSITY) > 3 },
            "반환 건수보다 큰 순위가 나와야 모수가 후보 전체라는 뜻이다",
        )
    }

    @Test
    fun `동점은 같은 순위를 받고 다음 순위를 건너뛴다 — 1, 2, 2, 4다`() {
        val service = service(
            listOf(
                fixture("11110", mapOf(RegionScoreFactor.STAY_INTENSITY to 90.0)),
                fixture("11170", mapOf(RegionScoreFactor.STAY_INTENSITY to 50.0)),
                fixture("11200", mapOf(RegionScoreFactor.STAY_INTENSITY to 50.0)),
                fixture("11215", mapOf(RegionScoreFactor.STAY_INTENSITY to 10.0)),
            ),
        )

        val regions = service.recommendedRegions(query(), DomainLocale.KO).regions.associateBy { it.code }

        assertEquals(1, regions.getValue("11110").rankOf(RegionScoreFactor.STAY_INTENSITY))
        assertEquals(2, regions.getValue("11170").rankOf(RegionScoreFactor.STAY_INTENSITY))
        assertEquals(2, regions.getValue("11200").rankOf(RegionScoreFactor.STAY_INTENSITY))
        assertEquals(
            4,
            regions.getValue("11215").rankOf(RegionScoreFactor.STAY_INTENSITY),
            "동점 다음은 3이 아니라 4다(표준 경쟁 순위)",
        )
    }

    @Test
    fun `항목마다 모수가 다르다 — 결측 지역은 그 항목의 순위에서 빠진다`() {
        // 소비 강도가 있는 지역이 둘뿐이면 그 항목의 최하위는 3위가 아니라 2위다.
        val service = service(
            listOf(
                fixture(
                    "11110",
                    mapOf(
                        RegionScoreFactor.STAY_INTENSITY to 90.0,
                        RegionScoreFactor.SPENDING_INTENSITY to 90.0,
                    ),
                ),
                fixture("11170", mapOf(RegionScoreFactor.STAY_INTENSITY to 50.0)),
                fixture(
                    "11200",
                    mapOf(
                        RegionScoreFactor.STAY_INTENSITY to 10.0,
                        RegionScoreFactor.SPENDING_INTENSITY to 10.0,
                    ),
                ),
            ),
        )

        val regions = service.recommendedRegions(query(), DomainLocale.KO).regions.associateBy { it.code }

        assertEquals(3, regions.getValue("11200").rankOf(RegionScoreFactor.STAY_INTENSITY))
        assertEquals(2, regions.getValue("11200").rankOf(RegionScoreFactor.SPENDING_INTENSITY))
    }

    @Test
    fun `거리도 순위를 받는다 — 가까울수록 1위다`() {
        // 대표 좌표만 다르게 둔다. 거리는 적재된 지표가 아니라 요청 시점 계산이지만,
        // 값이 있는 이상 다른 항목과 같은 규칙으로 순위가 붙는다.
        fun at(code: String, latitude: Double) = RegionFixture(
            region = RegionRow(
                code = code,
                provinceCode = "11",
                name = AreaName(ko = "테스트구$code", en = "District $code", ja = null, zh = null),
                provinceName = AreaName(ko = "서울특별시", en = "Seoul", ja = null, zh = null),
                center = Coordinates(latitude = latitude, longitude = 127.05),
            ),
            metrics = mapOf(RegionScoreFactor.STAY_INTENSITY to RegionMetric(50.0, today)),
        )

        val service = service(listOf(at("11110", 37.90), at("11170", 37.50), at("11200", 37.70)))

        val regions = service.recommendedRegions(query(), DomainLocale.KO).regions.associateBy { it.code }

        // origin이 위도 37.5라 11170이 가장 가깝다.
        assertEquals(1, regions.getValue("11170").rankOf(RegionScoreFactor.DISTANCE))
        assertEquals(2, regions.getValue("11200").rankOf(RegionScoreFactor.DISTANCE))
        assertEquals(3, regions.getValue("11110").rankOf(RegionScoreFactor.DISTANCE))
    }

    @Test
    fun `근거가 얕아 제외된 지역은 모수에서 빠진다 — 순위는 남은 후보에서 매긴다`() {
        // 최소 항목 수 2에 걸려 탈락하는 지역(지표 없음 = 거리 1항목뿐)이 값은 가장 높다.
        // 탈락 전 집합에서 순위를 매기면 남은 지역들이 한 칸씩 밀려 2·3위가 된다.
        val service = service(
            listOf(
                fixture("11110", mapOf(RegionScoreFactor.STAY_INTENSITY to 50.0)),
                fixture("11170", mapOf(RegionScoreFactor.STAY_INTENSITY to 10.0)),
                fixture("11200", emptyMap()),
            ),
            policy = policy(minFactorCount = 2),
        )

        val regions = service.recommendedRegions(query(), DomainLocale.KO).regions.associateBy { it.code }

        assertEquals(setOf("11110", "11170"), regions.keys)
        assertEquals(1, regions.getValue("11110").rankOf(RegionScoreFactor.DISTANCE))
        assertEquals(1, regions.getValue("11110").rankOf(RegionScoreFactor.STAY_INTENSITY))
        assertEquals(2, regions.getValue("11170").rankOf(RegionScoreFactor.STAY_INTENSITY))
    }

    @Test
    fun `전 지역이 같은 값이면 모두 1위다 — 정규화 구간 폭이 0인 경우다`() {
        val service = service(
            listOf(
                fixture("11110", mapOf(RegionScoreFactor.STAY_INTENSITY to 50.0)),
                fixture("11170", mapOf(RegionScoreFactor.STAY_INTENSITY to 50.0)),
                fixture("11200", mapOf(RegionScoreFactor.STAY_INTENSITY to 50.0)),
            ),
        )

        val regions = service.recommendedRegions(query(), DomainLocale.KO).regions

        assertTrue(regions.all { it.valueOf(RegionScoreFactor.STAY_INTENSITY) == 50.0 })
        assertEquals(listOf(1, 1, 1), regions.map { it.rankOf(RegionScoreFactor.STAY_INTENSITY) })
    }
}
