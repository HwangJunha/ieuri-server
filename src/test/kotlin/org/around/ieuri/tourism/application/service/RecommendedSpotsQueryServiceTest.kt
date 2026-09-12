package org.around.ieuri.tourism.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.common.event.LocationAccessed
import org.around.ieuri.tourism.application.exception.InvalidCoordinatesException
import org.around.ieuri.tourism.application.exception.RadiusTooLargeException
import org.around.ieuri.tourism.application.policy.RecommendationPolicy
import org.around.ieuri.tourism.application.policy.SpotQueryPolicy
import org.around.ieuri.tourism.application.port.out.event.TourismEventPublisherPort
import org.around.ieuri.tourism.application.port.out.query.NearbySpotSearch
import org.around.ieuri.tourism.application.port.out.query.SpotQueryPort
import org.around.ieuri.tourism.application.port.out.query.SpotSearchResult
import org.around.ieuri.tourism.application.port.out.query.SpotSpatialQueryPort
import org.around.ieuri.tourism.application.query.RecommendedSpotsQuery
import org.around.ieuri.tourism.domain.specification.TaxRefundTitleRule
import org.around.ieuri.tourism.domain.strategy.CategoryDiversity
import org.around.ieuri.tourism.domain.strategy.RecommendationScore
import org.around.ieuri.tourism.domain.vo.CategorySelection
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** SPEC-0035 — 추천 묶음. 포트는 전부 스텁이다. */
class RecommendedSpotsQueryServiceTest {

    private val spotQueryPort = mockk<SpotQueryPort>()
    private val spotSpatialQueryPort = mockk<SpotSpatialQueryPort>()
    private val eventPublisher = mockk<TourismEventPublisherPort>(relaxed = true)
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
    private val policy = RecommendationPolicy(
        score = RecommendationScore(5_000.0, 0.8, TaxRefundTitleRule(listOf("[Tax Refund Shop]"))),
        diversity = CategoryDiversity(penalty = 0.1),
        candidateCap = 300,
    )
    private val service = RecommendedSpotsQueryService(spotQueryPort, spotSpatialQueryPort, eventPublisher, spotQueryPolicy, policy)

    private val shopping = SpotCategory("SHOPPING")
    private val restaurant = SpotCategory("RESTAURANT")

    @Test
    fun `범위 밖 좌표와 반경 상한 초과는 spots와 같은 코드로 거절된다`() {
        assertFailsWith<InvalidCoordinatesException> { service.recommendedSpots(query().copy(latitude = 91.0), DomainLocale.EN, 7L) }
        assertFailsWith<RadiusTooLargeException> { service.recommendedSpots(query().copy(radiusMeters = 100_001), DomainLocale.EN, 7L) }
        verify(exactly = 0) { spotSpatialQueryPort.searchNearby(any()) }
    }

    @Test
    fun `반응한 장소는 DB 단계 제외 집합으로 넘어가고 후보는 가까운 순 N건이다`() {
        every { spotQueryPort.findReactedSpotIds(7L, DomainLocale.EN) } returns setOf(2L, 5L)
        val search = slot<NearbySpotSearch>()
        every { spotSpatialQueryPort.searchNearby(capture(search)) } returns emptyList()

        service.recommendedSpots(query(), DomainLocale.EN, 7L)

        assertEquals(setOf(2L, 5L), search.captured.excludedSpotIds)
        assertEquals(DomainLocale.EN, search.captured.language)
        assertEquals(300, search.captured.limit)
        assertEquals(0, search.captured.offset)
    }

    @Test
    fun `옷가게만 가까워도 다른 카테고리가 사이에 들어오고 앞 first건만 준다`() {
        every { spotQueryPort.findReactedSpotIds(any(), any()) } returns emptySet()
        every { spotSpatialQueryPort.searchNearby(any()) } returns listOf(
            // 거리 요인: 100m≈0.980, 110m≈0.978, 300m≈0.943, 500m≈0.909. 옷가게 두 번째는 0.1 깎여 0.878.
            card(1, 100.0, shopping), card(2, 110.0, shopping), card(3, 120.0, shopping),
            card(4, 300.0, restaurant), card(5, 500.0, SpotCategory.TOURIST_SPOT),
        )

        val result = service.recommendedSpots(query(first = 4), DomainLocale.EN, 7L)

        assertEquals(listOf(1L, 4L, 5L, 2L), result.map { it.id })
    }

    @Test
    fun `면세 태그 장소는 감점되지만 숨겨지지 않는다`() {
        every { spotQueryPort.findReactedSpotIds(any(), any()) } returns emptySet()
        every { spotSpatialQueryPort.searchNearby(any()) } returns listOf(
            card(1, 100.0, shopping, title = "[Tax Refund Shop] 나이키"),
            card(2, 100.0, shopping, title = "무인양품"),
        )

        val result = service.recommendedSpots(query(), DomainLocale.EN, 7L)

        assertEquals(listOf(2L, 1L), result.map { it.id })
    }

    @Test
    fun `first는 상한으로 절삭되고 후보가 적으면 있는 만큼 준다`() {
        every { spotQueryPort.findReactedSpotIds(any(), any()) } returns emptySet()
        every { spotSpatialQueryPort.searchNearby(any()) } returns listOf(card(1, 100.0, shopping))

        assertEquals(1, service.recommendedSpots(query(first = 500), DomainLocale.EN, 7L).size)
    }

    @Test
    fun `위치 조회 사실을 좌표 없이 감사 이벤트로 남긴다`() {
        every { spotQueryPort.findReactedSpotIds(any(), any()) } returns emptySet()
        every { spotSpatialQueryPort.searchNearby(any()) } returns emptyList()

        service.recommendedSpots(query(), DomainLocale.EN, 7L)

        verify(exactly = 1) { eventPublisher.publish(LocationAccessed(7L, "recommendedSpots")) }
    }

    private fun card(id: Long, distance: Double, category: SpotCategory, title: String = "spot-$id") = SpotSearchResult(
        spotId = id,
        title = title,
        address = null,
        thumbnailImageUrl = "https://img/$id.jpg",
        mapX = 126.88,
        mapY = 37.48,
        category = category,
        copyright = CopyrightType.UNSPECIFIED,
        distanceMeters = distance,
    )

    private fun query(first: Int = 20) = RecommendedSpotsQuery(
        latitude = 37.48,
        longitude = 126.88,
        radiusMeters = 20_000,
        categories = CategorySelection.UNFILTERED,
        first = first,
    )
}
