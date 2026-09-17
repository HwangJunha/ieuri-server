package org.around.ieuri.tourism.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.common.event.LocationAccessed
import org.around.ieuri.tourism.application.exception.BboxTooLargeException
import org.around.ieuri.tourism.application.policy.EmbeddingSpec
import org.around.ieuri.tourism.application.policy.MapViewportPolicy
import org.around.ieuri.tourism.application.policy.RecommendationPolicy
import org.around.ieuri.tourism.application.port.out.event.TourismEventPublisherPort
import org.around.ieuri.tourism.application.port.out.query.MapCandidateSearch
import org.around.ieuri.tourism.application.port.out.query.MapCandidateView
import org.around.ieuri.tourism.application.port.out.query.MapQueryPort
import org.around.ieuri.tourism.application.port.out.query.SpotQueryPort
import org.around.ieuri.tourism.application.port.out.query.TasteSimilarityQueryPort
import org.around.ieuri.tourism.application.query.MapRecommendedSpotsQuery
import org.around.ieuri.tourism.domain.specification.TaxRefundTitleRule
import org.around.ieuri.tourism.domain.strategy.CategoryDiversity
import org.around.ieuri.tourism.domain.strategy.RecommendationScore
import org.around.ieuri.tourism.domain.vo.CategorySelection
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.MapBounds
import org.around.ieuri.tourism.domain.vo.ReactionType
import org.around.ieuri.tourism.domain.vo.SpotCategory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** SPEC-0036 — 지도 영역 추천. 포트는 전부 스텁이며 DB·Spring을 세우지 않는다. */
class MapRecommendationServiceTest {

    private val mapQueryPort = mockk<MapQueryPort>()
    private val spotQueryPort = mockk<SpotQueryPort>()
    private val tasteSimilarityQueryPort = mockk<TasteSimilarityQueryPort>()
    private val eventPublisher = mockk<TourismEventPublisherPort>(relaxed = true)

    private val viewportPolicy = MapViewportPolicy(
        minZoom = 1,
        maxZoom = 14,
        maxAreaInSquareDegrees = 4.0,
        maxSpotMarkers = 500,
        maxEventMarkers = 200,
        recommendationCandidateCap = 2_000,
        maxRecommendationSize = 100,
    )
    private val recommendationPolicy = RecommendationPolicy(
        score = RecommendationScore(
            distanceHalfLifeMeters = 5_000.0,
            taxRefundMultiplier = 0.8,
            taxRefundTitleRule = TaxRefundTitleRule(listOf("[Tax Refund Shop]")),
            tasteWeight = 0.5,
        ),
        diversity = CategoryDiversity(penalty = 0.1),
        candidateCap = 300,
        tasteMinReactions = 3,
        embedding = EmbeddingSpec(model = "voyage-4", dimension = 4, retryCount = 1, overviewMaxChars = 500),
    )
    private val service = MapRecommendationService(
        mapQueryPort,
        spotQueryPort,
        tasteSimilarityQueryPort,
        eventPublisher,
        viewportPolicy,
        recommendationPolicy,
    )

    private val shopping = SpotCategory("SHOPPING")
    private val restaurant = SpotCategory("RESTAURANT")

    /** 서울 도심 근방의 작은 사각형. 중심은 (37.55, 126.99)다. */
    private val seoul = MapBounds(minLat = 37.54, minLng = 126.98, maxLat = 37.56, maxLng = 127.00)

    /** 부산 서면 근방. AC-003에서 서울과 겹치지 않는 영역으로 쓴다. */
    private val busan = MapBounds(minLat = 35.14, minLng = 129.04, maxLat = 35.18, maxLng = 129.08)

    private fun query(
        bounds: MapBounds = seoul,
        excluded: Set<Long> = emptySet(),
        first: Int = 10,
    ) = MapRecommendedSpotsQuery(
        bounds = bounds,
        excludedSpotIds = excluded,
        categories = CategorySelection.ofValues(emptySet()),
        first = first,
    )

    private fun candidate(
        id: Long,
        distanceMeters: Double,
        category: SpotCategory = shopping,
        title: String = "장소 $id",
    ) = MapCandidateView(
        id = id,
        title = title,
        thumbnailImageUrl = "https://img.example/$id.jpg",
        category = category,
        lng = 126.99,
        lat = 37.55,
        distanceMeters = distanceMeters,
    )

    private fun noReactions() {
        every { spotQueryPort.findReactedSpotIdsByReaction(any(), any()) } returns emptyMap()
    }

    private fun candidatesAre(vararg items: MapCandidateView) {
        every { mapQueryPort.findRecommendationCandidatesWithin(any()) } returns items.toList()
    }

    @Test
    fun `AC-001 영역 안 후보가 많아도 앞 first건만 나오고 점수 높은 순이다`() {
        noReactions()
        // 거리가 멀수록 점수가 낮다. 카테고리를 갈라 반복 감점이 순서를 흔들지 않게 둔다.
        val many = (1..50).map {
            candidate(it.toLong(), distanceMeters = it * 100.0, category = if (it % 2 == 0) shopping else restaurant)
        }
        every { mapQueryPort.findRecommendationCandidatesWithin(any()) } returns many

        val result = service.mapRecommendedSpots(query(first = 10), DomainLocale.EN, 7L)

        assertEquals(10, result.size)
        assertContentEquals((1L..10L).toList(), result.map { it.id })
    }

    @Test
    fun `AC-002 같은 조건으로 두 번 계산하면 같은 순서다`() {
        noReactions()
        val items = listOf(
            candidate(1L, 900.0, restaurant),
            candidate(2L, 400.0, shopping),
            candidate(3L, 700.0, shopping),
        )
        every { mapQueryPort.findRecommendationCandidatesWithin(any()) } returns items

        val first = service.mapRecommendedSpots(query(), DomainLocale.EN, 7L).map { it.id }
        val second = service.mapRecommendedSpots(query(), DomainLocale.EN, 7L).map { it.id }

        assertContentEquals(first, second)
    }

    @Test
    fun `AC-003 조회 조건의 영역은 요청이 준 영역 그대로다`() {
        noReactions()
        val search = slot<MapCandidateSearch>()
        every { mapQueryPort.findRecommendationCandidatesWithin(capture(search)) } returns emptyList()

        service.mapRecommendedSpots(query(bounds = busan), DomainLocale.EN, 7L)

        assertEquals(busan, search.captured.bounds)
        // 거리 기준점은 요청 영역의 중심점이며 회원의 실제 위치가 아니다.
        assertEquals(35.16, search.captured.bounds.center.latitude, 1e-9)
        assertEquals(129.06, search.captured.bounds.center.longitude, 1e-9)
    }

    @Test
    fun `AC-004 요청이 준 제외 id는 반응과 합쳐져 조회 조건으로 넘어간다`() {
        every { spotQueryPort.findReactedSpotIdsByReaction(7L, DomainLocale.EN) } returns
            mapOf(ReactionType.LIKE to setOf(2L), ReactionType.DISLIKE to setOf(5L))
        val search = slot<MapCandidateSearch>()
        every { mapQueryPort.findRecommendationCandidatesWithin(capture(search)) } returns emptyList()

        service.mapRecommendedSpots(query(excluded = setOf(11L, 12L)), DomainLocale.EN, 7L)

        assertEquals(setOf(2L, 5L, 11L, 12L), search.captured.excludedSpotIds)
        assertEquals(DomainLocale.EN, search.captured.language)
        assertEquals(2_000, search.captured.limit)
    }

    @Test
    fun `AC-007 남은 후보가 first보다 적으면 있는 만큼 돌려준다`() {
        noReactions()
        candidatesAre(candidate(1L, 100.0), candidate(2L, 200.0), candidate(3L, 300.0))

        val result = service.mapRecommendedSpots(query(first = 10), DomainLocale.EN, 7L)

        assertEquals(3, result.size)
    }

    @Test
    fun `AC-009 후보가 남아 있으면 요청 크기만큼 채워 소진으로 보이지 않는다`() {
        noReactions()
        val remaining = (11..40).map { candidate(it.toLong(), it * 50.0) }
        every { mapQueryPort.findRecommendationCandidatesWithin(any()) } returns remaining

        val result = service.mapRecommendedSpots(query(first = 10), DomainLocale.EN, 7L)

        assertEquals(10, result.size)
    }

    @Test
    fun `AC-011 싫어요한 장소는 조회 조건의 제외 집합에 들어간다`() {
        every { spotQueryPort.findReactedSpotIdsByReaction(7L, DomainLocale.EN) } returns
            mapOf(ReactionType.DISLIKE to setOf(42L))
        val search = slot<MapCandidateSearch>()
        every { mapQueryPort.findRecommendationCandidatesWithin(capture(search)) } returns emptyList()

        service.mapRecommendedSpots(query(), DomainLocale.EN, 7L)

        assertTrue(42L in search.captured.excludedSpotIds)
    }

    @Test
    fun `AC-012 영역 안 후보가 0건이면 오류가 아니라 빈 결과다`() {
        noReactions()
        candidatesAre()

        assertEquals(emptyList(), service.mapRecommendedSpots(query(), DomainLocale.EN, 7L))
    }

    @Test
    fun `AC-013 감사 기록에는 회원과 연산명만 담기고 좌표는 담기지 않는다`() {
        noReactions()
        candidatesAre()
        val event = slot<LocationAccessed>()
        every { eventPublisher.publish(capture(event)) } returns Unit

        service.mapRecommendedSpots(query(), DomainLocale.EN, 7L)

        assertEquals(7L, event.captured.memberId)
        assertEquals("mapRecommendedSpots", event.captured.operation)
    }

    @Test
    fun `면적 상한을 넘는 영역은 조회 전에 거절된다`() {
        val huge = MapBounds(minLat = 30.0, minLng = 120.0, maxLat = 35.0, maxLng = 130.0)

        assertFailsWith<BboxTooLargeException> {
            service.mapRecommendedSpots(query(bounds = huge), DomainLocale.EN, 7L)
        }
        verify(exactly = 0) { mapQueryPort.findRecommendationCandidatesWithin(any()) }
        verify(exactly = 0) { eventPublisher.publish(any()) }
    }

    @Test
    fun `묶음 크기는 상한으로 절삭되고 거절되지 않는다`() {
        noReactions()
        val many = (1..200).map { candidate(it.toLong(), it * 10.0) }
        every { mapQueryPort.findRecommendationCandidatesWithin(any()) } returns many

        val result = service.mapRecommendedSpots(query(first = 500), DomainLocale.EN, 7L)

        assertEquals(100, result.size)
    }

    @Test
    fun `반응이 최소량 미만이면 취향을 조회하지 않는다`() {
        every { spotQueryPort.findReactedSpotIdsByReaction(7L, DomainLocale.EN) } returns
            mapOf(ReactionType.LIKE to setOf(1L))
        candidatesAre(candidate(9L, 100.0))

        service.mapRecommendedSpots(query(), DomainLocale.EN, 7L)

        verify(exactly = 0) { tasteSimilarityQueryPort.findSimilarities(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `취향 조회가 실패해도 오류가 아니라 거리만으로 계산한다`() {
        every { spotQueryPort.findReactedSpotIdsByReaction(7L, DomainLocale.EN) } returns
            mapOf(ReactionType.LIKE to setOf(1L, 2L), ReactionType.DISLIKE to setOf(3L))
        candidatesAre(candidate(9L, 100.0), candidate(10L, 900.0))
        every {
            tasteSimilarityQueryPort.findSimilarities(any(), any(), any(), any(), any())
        } throws RuntimeException("pgvector 실패")

        val result = service.mapRecommendedSpots(query(), DomainLocale.EN, 7L)

        assertContentEquals(listOf(9L, 10L), result.map { it.id })
    }

    @Test
    fun `면세 태그 장소는 배율만큼 깎여 같은 거리의 일반 장소보다 뒤에 온다`() {
        noReactions()
        candidatesAre(
            candidate(1L, 100.0, restaurant, title = "가게[Tax Refund Shop]"),
            candidate(2L, 100.0, shopping, title = "그냥 가게"),
        )

        val result = service.mapRecommendedSpots(query(), DomainLocale.EN, 7L)

        assertContentEquals(listOf(2L, 1L), result.map { it.id })
    }
}
