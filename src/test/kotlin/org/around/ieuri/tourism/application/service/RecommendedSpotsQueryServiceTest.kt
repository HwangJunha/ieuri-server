package org.around.ieuri.tourism.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.tourism.application.exception.CursorExpiredException
import org.around.ieuri.tourism.application.exception.InvalidCursorException
import org.around.ieuri.tourism.application.exception.RadiusTooLargeException
import org.around.ieuri.tourism.application.policy.RecommendationPolicy
import org.around.ieuri.tourism.application.policy.SpotQueryPolicy
import org.around.ieuri.tourism.application.port.out.client.ActivityCounts
import org.around.ieuri.tourism.application.port.out.client.CommunityActivityCountPort
import org.around.ieuri.tourism.application.port.out.client.ExternalTrendScorePort
import org.around.ieuri.tourism.application.port.out.query.NearbySpotSearch
import org.around.ieuri.tourism.application.port.out.query.ReactionCounts
import org.around.ieuri.tourism.application.port.out.query.SpotQueryPort
import org.around.ieuri.tourism.application.port.out.query.SpotSearchResult
import org.around.ieuri.tourism.application.port.out.query.SpotSpatialQueryPort
import org.around.ieuri.tourism.application.query.LocationViewQuery
import org.around.ieuri.tourism.domain.specification.TaxRefundTitleRule
import org.around.ieuri.tourism.domain.strategy.DppReranker
import org.around.ieuri.tourism.domain.strategy.RecommendationScore
import org.around.ieuri.tourism.domain.strategy.SpotSimilarity
import org.around.ieuri.tourism.domain.vo.CategorySelection
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.RecommendedSpotsCursor
import org.around.ieuri.tourism.domain.vo.ScoreWeights
import org.around.ieuri.tourism.domain.vo.SpotCategory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * SPEC-0035 R4~R16 — 추천 목록의 첫 요청·이어 받기·결정성. 포트는 전부 스텁이다.
 *
 * 숫자를 작게 잡는다: 후보 상한 N=6, 창 k=2. 후보는 거리순 id 1..6이다.
 */
class RecommendedSpotsQueryServiceTest {

    private val spotQueryPort = mockk<SpotQueryPort>()
    private val spotSpatialQueryPort = mockk<SpotSpatialQueryPort>()
    private val activityCountPort = mockk<CommunityActivityCountPort>()
    private val trendScorePort = mockk<ExternalTrendScorePort>()
    private val now = Instant.parse("2026-09-11T09:00:00Z")

    private val spotQueryPolicy = SpotQueryPolicy(
        maxRadiusMeters = 100_000,
        maxPageSize = 100,
        maxLikedPageSize = 100,
        detailAbsenceTtl = Duration.ofDays(7),
        maxSearchPageSize = 50,
        maxSuggestionCount = 10,
        maxSearchQueryLength = 100,
        maxSearchPage = 500,
        minPopularLikeCount = 5,
    )

    private fun policy(alpha: Double = 1.0, trendWeight: Double = 0.0, candidateCap: Int = 6) =
        RecommendationPolicy(
            score = RecommendationScore(
                weights = ScoreWeights(distance = 0.4, popularity = 0.25, activity = 0.15, affinity = 0.2, trend = trendWeight),
                distanceHalfLifeMeters = 5_000.0,
                popularityCap = 100,
                activityCap = 50,
                taxRefundMultiplier = 0.8,
                taxRefundTitleRule = TaxRefundTitleRule(listOf("[Tax Refund Shop]")),
            ),
            reranker = DppReranker(alpha = alpha, sigma = 0.5, window = 2),
            similarity = SpotSimilarity(sameCategoryDistance = 0.3, differentCategoryDistance = 1.0),
            candidateCap = candidateCap,
            cursorMaxAge = Duration.ofHours(24),
        )

    private fun service(policy: RecommendationPolicy = policy(), clock: Clock = Clock.fixed(now, ZoneOffset.UTC)) =
        RecommendedSpotsQueryService(
            spotQueryPort, spotSpatialQueryPort, activityCountPort, trendScorePort, spotQueryPolicy, policy, clock,
        )

    private val candidates = (1L..6L).map { card(it) }

    private fun stubDefaults(cards: List<SpotSearchResult> = candidates) {
        every { spotSpatialQueryPort.searchNearby(any()) } returns cards
        every { spotQueryPort.countReactionsIn(any(), any()) } returns emptyMap()
        every { activityCountPort.countByCatalogIds(any()) } returns emptyMap()
        every { trendScorePort.findByCatalogIds(any()) } returns emptyMap()
    }

    // ── 검증 ────────────────────────────────────────────────────────────────

    @Test
    fun `반경 상한 초과는 RADIUS_TOO_LARGE다 - AC-2_1`() {
        assertFailsWith<RadiusTooLargeException> {
            service().recommendedSpots(query(first = 2).copy(radiusMeters = 100_001), DomainLocale.EN, null)
        }
    }

    @Test
    fun `first는 상한으로 절삭된다 - AC-2_3`() {
        stubDefaults()
        val page = service().recommendedSpots(query(first = 500), DomainLocale.EN, null)
        assertEquals(6, page.edges.size)
        assertFalse(page.pageInfo.hasNextPage)
    }

    // ── 후보·제외 ────────────────────────────────────────────────────────────

    @Test
    fun `로그인 회원이 반응한 장소는 후보에서 빠진 채 진행된다 - AC-5_1`() {
        stubDefaults()
        every { spotQueryPort.findReactedSpotIds(9L, DomainLocale.EN) } returns setOf(2L, 3L)
        val search = slot<NearbySpotSearch>()
        every { spotSpatialQueryPort.searchNearby(capture(search)) } returns candidates.filter { it.spotId !in setOf(2L, 3L) }

        service().recommendedSpots(query(first = 10), DomainLocale.EN, memberId = 9L)

        assertEquals(setOf(2L, 3L), search.captured.excludedSpotIds)
    }

    @Test
    fun `비로그인은 제외 없이 진행된다 - AC-5_2`() {
        stubDefaults()
        val search = slot<NearbySpotSearch>()
        every { spotSpatialQueryPort.searchNearby(capture(search)) } returns candidates

        service().recommendedSpots(query(first = 10), DomainLocale.EN, null)

        assertTrue(search.captured.excludedSpotIds.isEmpty())
        verify(exactly = 0) { spotQueryPort.findReactedSpotIds(any(), any()) }
    }

    @Test
    fun `후보는 거리 가까운 순 N건만 대상이다 - AC-15_1`() {
        stubDefaults()
        val search = slot<NearbySpotSearch>()
        every { spotSpatialQueryPort.searchNearby(capture(search)) } returns candidates

        service(policy(candidateCap = 300)).recommendedSpots(query(first = 2), DomainLocale.EN, null)

        assertEquals(300, search.captured.limit)
        assertEquals(0, search.captured.offset)
    }

    // ── 점수 요인의 부재 ──────────────────────────────────────────────────────

    @Test
    fun `집계 포트가 모르는 장소는 요인 없음이며 0건과 구분된다 - AC-8_3 AC-9_3`() {
        // 거리가 같은 두 후보. 1번은 반응 0건(알려짐), 2번은 포트가 모름(요인 없음).
        stubDefaults(listOf(card(1, distance = 1_000.0), card(2, distance = 1_000.0)))
        every { spotQueryPort.countReactionsIn(any(), any()) } returns mapOf(1L to ReactionCounts(0, 0))
        every { activityCountPort.countByCatalogIds(any()) } returns mapOf(1L to ActivityCounts(0, 0, 0))

        val page = service(policy(alpha = 0.0)).recommendedSpots(query(first = 2), DomainLocale.EN, null)

        // 요인이 없는 2번은 거리만으로 점수가 나와 0으로 눌린 1번보다 앞선다.
        assertEquals(listOf(2L, 1L), page.edges.map { it.node.id })
    }

    @Test
    fun `유행 포트가 값을 주면 반영되고 없으면 나머지로만 계산한다 - AC-12_1 AC-12_2`() {
        stubDefaults(listOf(card(1, distance = 1_000.0), card(2, distance = 1_000.0)))
        every { trendScorePort.findByCatalogIds(any()) } returns mapOf(2L to 1.0)

        val page = service(policy(alpha = 0.0, trendWeight = 0.5)).recommendedSpots(query(first = 2), DomainLocale.EN, null)

        assertEquals(listOf(2L, 1L), page.edges.map { it.node.id })
    }

    @Test
    fun `집계 포트가 실패해도 요인 없음으로 응답한다`() {
        stubDefaults()
        every { spotQueryPort.countReactionsIn(any(), any()) } throws IllegalStateException("db down")
        every { activityCountPort.countByCatalogIds(any()) } throws IllegalStateException("db down")

        val page = service().recommendedSpots(query(first = 6), DomainLocale.EN, null)

        assertEquals(6, page.edges.size)
    }

    // ── 페이지·이어 받기 ─────────────────────────────────────────────────────

    @Test
    fun `첫 요청은 앞 first건을 주고 커서 위치는 first다 - AC-15_2`() {
        stubDefaults()
        val page = service().recommendedSpots(query(first = 2), DomainLocale.EN, null)

        assertEquals(2, page.edges.size)
        assertTrue(page.pageInfo.hasNextPage)
        assertFalse(page.pageInfo.hasPreviousPage)
        assertEquals(2, RecommendedSpotsCursor.decode(page.pageInfo.endCursor!!).position)
        assertEquals(1, RecommendedSpotsCursor.decode(page.edges[0].cursor).position)
    }

    @Test
    fun `후보가 페이지보다 적으면 있는 만큼 주고 hasNextPage는 false다 - AC-15_3`() {
        stubDefaults(candidates.take(3))
        val page = service().recommendedSpots(query(first = 5), DomainLocale.EN, null)
        assertEquals(3, page.edges.size)
        assertFalse(page.pageInfo.hasNextPage)
    }

    @Test
    fun `이어 받기는 같은 순열의 다음 구간이며 끝까지 중복·누락이 없다 - AC-16_1 AC-16_3`() {
        stubDefaults()
        val p1 = service().recommendedSpots(query(first = 4), DomainLocale.EN, null)
        val p2 = service().recommendedSpots(query(first = 4, after = p1.pageInfo.endCursor), DomainLocale.EN, null)

        assertEquals(4, p1.edges.size)
        assertTrue(p1.pageInfo.hasNextPage)
        assertTrue(p2.pageInfo.hasPreviousPage)
        assertEquals(2, p2.edges.size)
        assertFalse(p2.pageInfo.hasNextPage)
        assertEquals((1L..6L).toSet(), (p1.edges + p2.edges).map { it.node.id }.toSet())
        assertEquals(6, RecommendedSpotsCursor.decode(p2.pageInfo.endCursor!!).position)
    }

    @Test
    fun `같은 입력은 요청마다 같은 순열이다 - AC-4_1 AC-13_3`() {
        stubDefaults()
        val a = service().recommendedSpots(query(first = 6), DomainLocale.EN, null)
        val b = service().recommendedSpots(query(first = 6), DomainLocale.EN, null)
        assertEquals(a.edges.map { it.node.id }, b.edges.map { it.node.id })
    }

    // ── 커서 ────────────────────────────────────────────────────────────────

    @Test
    fun `형식이 깨진 커서는 INVALID_CURSOR다 - AC-4_3`() {
        stubDefaults()
        assertFailsWith<InvalidCursorException> {
            service().recommendedSpots(query(first = 2, after = "garbage"), DomainLocale.EN, null)
        }
    }

    @Test
    fun `수명을 넘긴 커서는 CURSOR_EXPIRED다 - AC-4_4`() {
        stubDefaults()
        val first = service().recommendedSpots(query(first = 2), DomainLocale.EN, null)
        val later = Clock.fixed(now.plus(Duration.ofHours(25)), ZoneOffset.UTC)
        assertFailsWith<CursorExpiredException> {
            service(clock = later).recommendedSpots(query(first = 2, after = first.pageInfo.endCursor), DomainLocale.EN, null)
        }
    }

    @Test
    fun `이어 받은 커서는 세션 시작 시각을 그대로 이어 간다`() {
        stubDefaults()
        val first = service().recommendedSpots(query(first = 2), DomainLocale.EN, null)
        val later = Clock.fixed(now.plus(Duration.ofHours(1)), ZoneOffset.UTC)
        val second = service(clock = later).recommendedSpots(query(first = 2, after = first.pageInfo.endCursor), DomainLocale.EN, null)
        assertEquals(now, RecommendedSpotsCursor.decode(second.pageInfo.endCursor!!).issuedAt)
    }

    // ── 고정 데이터 ──────────────────────────────────────────────────────────

    private fun card(id: Long, distance: Double = id * 500.0, category: SpotCategory = SpotCategory.TOURIST_SPOT) =
        SpotSearchResult(
            spotId = id,
            title = "spot-$id",
            address = null,
            thumbnailImageUrl = "https://img/$id.jpg",
            mapX = 126.88,
            mapY = 37.48,
            category = category,
            copyright = CopyrightType.UNSPECIFIED,
            distanceMeters = distance,
        )

    private fun query(first: Int, after: String? = null) = LocationViewQuery(
        latitude = 37.48,
        longitude = 126.88,
        radiusMeters = 20_000,
        categories = CategorySelection.UNFILTERED,
        first = first,
        after = after,
    )
}
