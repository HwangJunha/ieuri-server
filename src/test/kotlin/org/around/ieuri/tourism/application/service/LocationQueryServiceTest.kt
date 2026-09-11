package org.around.ieuri.tourism.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.tourism.application.exception.InvalidCoordinatesException
import org.around.ieuri.tourism.application.exception.InvalidCursorException
import org.around.ieuri.tourism.application.exception.RadiusTooLargeException
import org.around.ieuri.tourism.application.policy.SpotQueryPolicy
import org.around.ieuri.tourism.application.port.out.query.NearbyKeysetSearch
import org.around.ieuri.tourism.application.port.out.query.SpotQueryPort
import org.around.ieuri.tourism.application.port.out.query.SpotSearchResult
import org.around.ieuri.tourism.application.port.out.query.SpotSpatialQueryPort
import org.around.ieuri.tourism.application.query.LocationViewQuery
import org.around.ieuri.tourism.domain.vo.CategorySelection
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** SPEC-0035 R1~R3 — `location` 진입 검증과 `Location.spots` 키셋 페이지. 포트는 전부 스텁이다. */
class LocationQueryServiceTest {

    private val spotQueryPort = mockk<SpotQueryPort>()
    private val spotSpatialQueryPort = mockk<SpotSpatialQueryPort>()
    private val policy = SpotQueryPolicy(
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
    private val service = LocationQueryService(spotQueryPort, spotSpatialQueryPort, policy)

    @Test
    fun `location은 좌표를 담은 맥락을 돌려주고 어떤 포트도 부르지 않는다 - AC-1_1`() {
        val view = service.location(37.48, 126.88)
        assertEquals(37.48, view.latitude)
        assertEquals(126.88, view.longitude)
        verify(exactly = 0) { spotSpatialQueryPort.searchNearbyAfter(any()) }
        verify(exactly = 0) { spotQueryPort.findReactedSpotIds(any(), any()) }
    }

    @Test
    fun `범위 밖 좌표는 spots와 같은 코드로 거절된다 - AC-1_2`() {
        assertFailsWith<InvalidCoordinatesException> { service.location(91.0, 126.88) }
        assertFailsWith<InvalidCoordinatesException> { service.location(37.48, 181.0) }
        assertFailsWith<InvalidCoordinatesException> { service.spots(query(first = 3).copy(latitude = 91.0), DomainLocale.EN, null) }
    }

    @Test
    fun `반경 상한 초과는 RADIUS_TOO_LARGE다 - AC-2_1`() {
        assertFailsWith<RadiusTooLargeException> {
            service.spots(query(first = 3).copy(radiusMeters = 100_001), DomainLocale.EN, null)
        }
    }

    @Test
    fun `first는 상한으로 절삭되고 오류가 아니다 - AC-2_3`() {
        stubKeyset(cards(7))
        val search = slot<NearbyKeysetSearch>()
        every { spotSpatialQueryPort.searchNearbyAfter(capture(search)) } returns emptyList()

        service.spots(query(first = 500), DomainLocale.EN, null)

        assertEquals(100, search.captured.limit)
    }

    @Test
    fun `키셋 커서로 이어 받으면 중복·누락 없이 거리순으로 전부 나온다 - AC-3_1`() {
        stubKeyset(cards(7))

        val page1 = service.spots(query(first = 3), DomainLocale.EN, null)
        val page2 = service.spots(query(first = 3, after = page1.pageInfo.endCursor), DomainLocale.EN, null)
        val page3 = service.spots(query(first = 3, after = page2.pageInfo.endCursor), DomainLocale.EN, null)

        assertTrue(page1.pageInfo.hasNextPage)
        assertTrue(page2.pageInfo.hasNextPage)
        assertFalse(page3.pageInfo.hasNextPage)
        val ids = (page1.edges + page2.edges + page3.edges).map { it.node.id }
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L), ids)
    }

    @Test
    fun `로그인 회원이 반응한 장소는 DB 단계 제외 집합으로 넘어간다 - AC-3_2`() {
        every { spotQueryPort.findReactedSpotIds(7L, DomainLocale.EN) } returns setOf(2L, 5L)
        val search = slot<NearbyKeysetSearch>()
        every { spotSpatialQueryPort.searchNearbyAfter(capture(search)) } returns emptyList()

        service.spots(query(first = 3), DomainLocale.EN, memberId = 7L)

        assertEquals(setOf(2L, 5L), search.captured.excludedSpotIds)
        assertEquals(DomainLocale.EN, search.captured.language)
    }

    @Test
    fun `손상된 커서는 INVALID_CURSOR다 - AC-3_3`() {
        assertFailsWith<InvalidCursorException> {
            service.spots(query(first = 3, after = "garbage"), DomainLocale.EN, null)
        }
    }

    /** 포트 스텁이 (거리, id) 키셋을 흉내 낸다 — `limit + 1`건을 돌려주는 규약 포함. */
    private fun stubKeyset(all: List<SpotSearchResult>) {
        every { spotSpatialQueryPort.searchNearbyAfter(any()) } answers {
            val q = firstArg<NearbyKeysetSearch>()
            all.filter { c ->
                val after = q.after
                after == null || c.distanceMeters > after.distanceMeters ||
                    (c.distanceMeters == after.distanceMeters && c.spotId > after.spotId)
            }.take(q.limit + 1)
        }
    }

    private fun cards(count: Int) = (1L..count).map { id ->
        SpotSearchResult(
            spotId = id,
            title = "spot-$id",
            address = null,
            thumbnailImageUrl = "https://img/$id.jpg",
            mapX = 126.88,
            mapY = 37.48,
            category = SpotCategory.TOURIST_SPOT,
            copyright = CopyrightType.UNSPECIFIED,
            distanceMeters = id * 100.0,
        )
    }

    private fun query(first: Int, after: String? = null) = LocationViewQuery(
        latitude = 37.48,
        longitude = 126.88,
        radiusMeters = 20_000,
        categories = CategorySelection.UNFILTERED,
        first = first,
        after = after,
    )
}
