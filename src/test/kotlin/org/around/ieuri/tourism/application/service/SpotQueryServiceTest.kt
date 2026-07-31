package org.around.ieuri.tourism.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.around.ieuri.tourism.application.exception.CategoryNotAvailableForLocaleException
import org.around.ieuri.tourism.application.exception.InvalidCoordinatesException
import org.around.ieuri.tourism.application.exception.RadiusTooLargeException
import org.around.ieuri.tourism.application.policy.SpotQueryPolicy
import org.around.ieuri.tourism.application.port.out.client.NearbySpotQuery
import org.around.ieuri.tourism.application.port.out.client.NearbySpotResult
import org.around.ieuri.tourism.application.port.out.client.TourInfoPort
import org.around.ieuri.tourism.application.query.SpotsQuery
import org.around.ieuri.tourism.domain.aggregate.Spot
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.Distance
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** SpotQueryService — 정렬·커서(AC-1), 검증(AC-6/9), 가용성(AC-8). */
class SpotQueryServiceTest {

    private val tourInfoPort = mockk<TourInfoPort>()
    private val policy = SpotQueryPolicy(
        maxRadiusMeters = 100_000,
        maxPageSize = 100,
        upstreamPageSize = 100,
        maxUpstreamPagesPerCursor = 3,
    )
    private val service = SpotQueryService(tourInfoPort, policy)

    private val center = Coordinates(37.5665, 126.9780)

    private fun spot(id: String, distanceMeters: Double): Spot = Spot(
        id = id,
        title = "spot-$id",
        address = "서울 어딘가",
        thumbnailImageUrl = "https://img/$id.jpg",
        coordinates = center,
        distance = Distance.ofMeters(distanceMeters),
        category = SpotCategory.TOURIST_SPOT,
        copyright = CopyrightType.UNSPECIFIED,
        attributionText = "출처: 한국관광공사",
    )

    private fun query(first: Int = 20, after: String? = null, category: SpotCategory? = null, radius: Int = 20000) =
        SpotsQuery(
            latitude = 37.5665, longitude = 126.9780,
            radiusMeters = radius, category = category, first = first, after = after,
        )

    @Test
    fun `결과는 거리 오름차순으로 정렬되고 커서로 다음 페이지가 이어진다`() {
        // upstream이 거리순이 아닌 순서로 준다.
        every { tourInfoPort.findNearby(any()) } returns NearbySpotResult(
            spots = listOf(spot("c", 300.0), spot("a", 100.0), spot("b", 200.0)),
            totalCount = 3,
            hasMoreUpstreamPages = false,
        )

        val page1 = service.spots(query(first = 2), DomainLocale.KO)
        assertEquals(listOf("a", "b"), page1.edges.map { it.node.id })
        assertTrue(page1.pageInfo.hasNextPage)

        val page2 = service.spots(query(first = 2, after = page1.pageInfo.endCursor), DomainLocale.KO)
        assertEquals(listOf("c"), page2.edges.map { it.node.id })
        assertFalse(page2.pageInfo.hasNextPage)
    }

    @Test
    fun `커서 진행에서 중복이 없다`() {
        every { tourInfoPort.findNearby(any()) } returns NearbySpotResult(
            spots = (1..5).map { spot("id$it", it * 100.0) },
            totalCount = 5,
            hasMoreUpstreamPages = false,
        )
        val seen = mutableListOf<String>()
        var after: String? = null
        repeat(3) {
            val page = service.spots(query(first = 2, after = after), DomainLocale.KO)
            seen += page.edges.map { it.node.id }
            after = page.pageInfo.endCursor
            if (!page.pageInfo.hasNextPage) return@repeat
        }
        assertEquals(seen.size, seen.toSet().size, "중복 발생: $seen")
    }

    @Test
    fun `distanceMeters가 응답에 채워진다`() {
        every { tourInfoPort.findNearby(any()) } returns NearbySpotResult(
            spots = listOf(spot("a", 123.4)),
            totalCount = 1,
            hasMoreUpstreamPages = false,
        )
        val page = service.spots(query(), DomainLocale.KO)
        assertEquals(123.4, page.edges.single().node.distanceMeters, 0.001)
    }

    @Test
    fun `반경 상한을 초과하면 RADIUS_TOO_LARGE`() {
        assertFailsWith<RadiusTooLargeException> {
            service.spots(query(radius = 100_001), DomainLocale.EN)
        }
    }

    @Test
    fun `위경도가 범위를 벗어나면 INVALID_COORDINATES`() {
        assertFailsWith<InvalidCoordinatesException> {
            service.spots(
                SpotsQuery(latitude = 999.0, longitude = 0.0, radiusMeters = 20000, category = null, first = 20, after = null),
                DomainLocale.EN,
            )
        }
    }

    @Test
    fun `현재 언어에 없는 카테고리는 CATEGORY_NOT_AVAILABLE_FOR_LOCALE`() {
        assertFailsWith<CategoryNotAvailableForLocaleException> {
            service.spots(query(category = SpotCategory.TRAVEL_COURSE), DomainLocale.EN) // 여행코스는 외국어 없음
        }
    }

    @Test
    fun `카테고리 미지정이면 upstream에 contentTypeId 없이 전달된다`() {
        val captured = slot<NearbySpotQuery>()
        every { tourInfoPort.findNearby(capture(captured)) } returns NearbySpotResult(emptyList(), 0, false)
        service.spots(query(category = null), DomainLocale.KO)
        assertEquals(null, captured.captured.category)
    }

    @Test
    fun `availableCategories는 언어별 유효 집합을 반환한다`() {
        assertTrue(SpotCategory.TRAVEL_COURSE in service.availableCategories(DomainLocale.KO))
        assertTrue(SpotCategory.TRANSPORT in service.availableCategories(DomainLocale.EN))
    }

    @Test
    fun `필터로 건수가 줄어도 보충하지 않고 hasNextPage로 알린다`() {
        // totalCount는 100이지만 필터 통과분은 1건 → 보충 없음.
        every { tourInfoPort.findNearby(any()) } returns NearbySpotResult(
            spots = listOf(spot("a", 50.0)),
            totalCount = 100,
            hasMoreUpstreamPages = true,
        )
        val page = service.spots(query(first = 20), DomainLocale.KO)
        assertEquals(1, page.edges.size)
        assertEquals(100, page.totalCount) // 참고용 상한(필터 이전)
    }
}
