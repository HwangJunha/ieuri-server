package org.around.ieuri.tourism.application.catalog

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.common.event.LocationAccessed
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.CursorPageInfo
import org.around.ieuri.tourism.application.catalog.provided.dto.LikedSpotView
import org.around.ieuri.tourism.application.catalog.provided.dto.LikedSpotsQuery
import org.around.ieuri.tourism.application.catalog.provided.dto.MapSpotView
import org.around.ieuri.tourism.application.catalog.provided.dto.MapViewportQuery
import org.around.ieuri.tourism.application.catalog.provided.dto.SpotView
import org.around.ieuri.tourism.application.catalog.provided.dto.SpotsQuery
import org.around.ieuri.tourism.application.catalog.provided.exception.BboxTooLargeException
import org.around.ieuri.tourism.application.catalog.provided.exception.EmptyQueryException
import org.around.ieuri.tourism.application.catalog.provided.exception.InvalidCoordinatesException
import org.around.ieuri.tourism.application.catalog.provided.exception.InvalidZoomException
import org.around.ieuri.tourism.application.catalog.provided.exception.QueryTooLongException
import org.around.ieuri.tourism.application.catalog.provided.exception.RadiusTooLargeException
import org.around.ieuri.tourism.application.catalog.required.CatalogEventPublisherPort
import org.around.ieuri.tourism.application.catalog.required.LikedSpotSearchPort
import org.around.ieuri.tourism.application.catalog.required.LikedSpotsSearch
import org.around.ieuri.tourism.application.catalog.required.MapQueryPort
import org.around.ieuri.tourism.application.catalog.required.MapViewportSearch
import org.around.ieuri.tourism.application.catalog.required.NearbySpotSearch
import org.around.ieuri.tourism.application.catalog.required.SpotBasics
import org.around.ieuri.tourism.application.catalog.required.SpotQueryPort
import org.around.ieuri.tourism.application.catalog.required.SpotSpatialQueryPort
import org.around.ieuri.tourism.application.congestion.provided.CongestionQueryUseCase
import org.around.ieuri.tourism.application.congestion.provided.dto.CulturalEventView
import org.around.ieuri.tourism.domain.catalog.CategorySelection
import org.around.ieuri.tourism.domain.catalog.ReactionType
import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.MapBounds
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** SpotCatalogQueryService — 주변 목록의 페이지·검증·반응 제외, 좋아요 목록과 그 안의 검색. */
class SpotCatalogQueryServiceTest {

    private val spotQueryPort = mockk<SpotQueryPort>()
    private val spotSpatialQueryPort = mockk<SpotSpatialQueryPort>()
    private val likedSpotSearchPort = mockk<LikedSpotSearchPort>()
    private val policy = SpotQueryPolicy(
        maxRadiusMeters = 100_000,
        maxPageSize = 100,
        maxLikedPageSize = 100,
        maxSearchPageSize = 50,
        maxSuggestionCount = 10,
        maxSearchQueryLength = 100,
        maxSearchPage = 500,
        minPopularLikeCount = 10,
    )
    private val mapQueryPort = mockk<MapQueryPort>()
    private val congestionQueryUseCase = mockk<CongestionQueryUseCase>()
    private val viewportPolicy = MapViewportPolicy(
        minZoom = 1,
        maxZoom = 14,
        maxAreaInSquareDegrees = 4.0,
        maxSpotMarkers = 3,
        maxEventMarkers = 2,
    )
    private val eventPublisher = mockk<CatalogEventPublisherPort>(relaxed = true)
    private val service = SpotCatalogQueryService(
        spotQueryPort,
        spotSpatialQueryPort,
        likedSpotSearchPort,
        mapQueryPort,
        congestionQueryUseCase,
        eventPublisher,
        policy,
        viewportPolicy,
    )
    private val seoulBounds = MapBounds(minLat = 37.4, minLng = 126.9, maxLat = 37.6, maxLng = 127.1)

    init {
        // 기본: 아무것도 반응하지 않음(빈 집합). 개별 테스트에서 override.
        every { spotQueryPort.findReactedSpotIds(any(), any()) } returns emptySet()
        every { spotSpatialQueryPort.searchNearby(any()) } returns emptyList()
        every { spotSpatialQueryPort.countNearby(any()) } returns 0
    }

    /** 읽기 DTO 픽스처. 거리는 DB가 계산한 값으로 직접 지정한다. */
    private fun result(id: Long, distanceMeters: Double): SpotView = SpotView(
        id = id,
        title = "spot-$id",
        address = "서울 어딘가",
        thumbnailImageUrl = "https://img/$id.jpg",
        mapX = 126.9780,
        mapY = 37.5665,
        category = SpotCategory.TOURIST_SPOT,
        copyright = CopyrightType.UNSPECIFIED,
        distanceMeters = distanceMeters,
    )

    /** 단일 카테고리 인자를 다중 선택으로 옮긴다. null이면 필터 없음. */
    private fun selection(category: SpotCategory?) = category?.let { CategorySelection.ofValues(setOf(it)) } ?: CategorySelection.UNFILTERED

    private fun query(page: Int = 1, size: Int = 100, category: SpotCategory? = null, radius: Int = 20000) = SpotsQuery(
        latitude = 37.5665,
        longitude = 126.9780,
        radiusMeters = radius,
        categories = selection(category),
        page = page,
        size = size,
    )

    @Test
    fun `DB가 돌려준 순서를 그대로 유지한다(AC-25)`() {
        every { spotSpatialQueryPort.searchNearby(any()) } returns
            listOf(result(1L, 100.0), result(2L, 200.0), result(3L, 300.0))
        every { spotSpatialQueryPort.countNearby(any()) } returns 3

        val result = service.spots(query(), DomainLocale.KO, memberId = null)

        assertEquals(listOf(1L, 2L, 3L), result.items.map { it.id })
    }

    @Test
    fun `page와 size를 limit·offset으로 변환한다`() {
        val captured = slot<NearbySpotSearch>()
        every { spotSpatialQueryPort.searchNearby(capture(captured)) } returns emptyList()

        service.spots(query(page = 3, size = 50), DomainLocale.KO, memberId = null)

        assertEquals(50, captured.captured.limit)
        assertEquals(100, captured.captured.offset) // (3-1) * 50
    }

    @Test
    fun `hasNext는 totalCount와 같은 조건에서 산출된다`() {
        every { spotSpatialQueryPort.searchNearby(any()) } returns listOf(result(1L, 100.0))
        every { spotSpatialQueryPort.countNearby(any()) } returns 200

        val result = service.spots(query(page = 1, size = 100), DomainLocale.KO, memberId = null)

        assertTrue(result.hasNext)
        assertEquals(1, result.page)
        assertEquals(200, result.totalCount)
    }

    @Test
    fun `마지막 페이지면 hasNext가 false다(AC-29)`() {
        every { spotSpatialQueryPort.searchNearby(any()) } returns listOf(result(1L, 100.0))
        every { spotSpatialQueryPort.countNearby(any()) } returns 100

        val result = service.spots(query(page = 1, size = 100), DomainLocale.KO, memberId = null)

        assertFalse(result.hasNext)
    }

    @Test
    fun `distanceMeters는 DB 계산 값을 그대로 노출한다(AC-26)`() {
        every { spotSpatialQueryPort.searchNearby(any()) } returns listOf(result(1L, 877.5))
        every { spotSpatialQueryPort.countNearby(any()) } returns 1

        val d = service.spots(query(), DomainLocale.KO, memberId = null).items.single().distanceMeters

        assertEquals(877.5, d)
    }

    @Test
    fun `size가 상한(100)을 넘으면 클램프된다(AC-30)`() {
        val captured = slot<NearbySpotSearch>()
        every { spotSpatialQueryPort.searchNearby(capture(captured)) } returns emptyList()

        service.spots(query(size = 500), DomainLocale.KO, memberId = null)

        assertEquals(100, captured.captured.limit)
    }

    @Test
    fun `반경 상한을 초과하면 RADIUS_TOO_LARGE`() {
        assertFailsWith<RadiusTooLargeException> {
            service.spots(query(radius = 100_001), DomainLocale.EN, memberId = null)
        }
    }

    @Test
    fun `위경도가 범위를 벗어나면 INVALID_COORDINATES`() {
        assertFailsWith<InvalidCoordinatesException> {
            service.spots(
                SpotsQuery(latitude = 999.0, longitude = 0.0, radiusMeters = 20000, categories = CategorySelection.UNFILTERED, page = 1, size = 100),
                DomainLocale.EN,
                memberId = null,
            )
        }
    }

    @Test
    fun `카테고리 필터가 조회 조건으로 전달된다(AC-33)`() {
        val captured = slot<NearbySpotSearch>()
        every { spotSpatialQueryPort.searchNearby(capture(captured)) } returns emptyList()

        service.spots(query(category = SpotCategory("LODGING")), DomainLocale.KO, memberId = null)

        assertEquals(CategorySelection.ofValues(setOf(SpotCategory("LODGING"))), captured.captured.categories)
    }

    @Test
    fun `로그인 회원이 이미 반응한 관광지는 DB 조회 단계에서 제외된다`() {
        val captured = slot<NearbySpotSearch>()
        every { spotQueryPort.findReactedSpotIds(7L, DomainLocale.KO) } returns setOf(2L)
        every { spotSpatialQueryPort.searchNearby(capture(captured)) } returns emptyList()

        service.spots(query(), DomainLocale.KO, memberId = 7L)

        assertEquals(setOf(2L), captured.captured.excludedSpotIds)
    }

    @Test
    fun `비로그인은 반응 조회 없이 전부 반환한다`() {
        val captured = slot<NearbySpotSearch>()
        every { spotSpatialQueryPort.searchNearby(capture(captured)) } returns
            listOf(result(1L, 100.0), result(2L, 200.0))
        every { spotSpatialQueryPort.countNearby(any()) } returns 2

        val result = service.spots(query(), DomainLocale.KO, memberId = null)

        assertEquals(listOf(1L, 2L), result.items.map { it.id })
        assertTrue(captured.captured.excludedSpotIds.isEmpty())
        verify(exactly = 0) { spotQueryPort.findReactedSpotIds(any(), any()) }
    }

    @Test
    fun `R-13 - 발견 큐의 제외 집합도 활성 언어 단위다`() {
        val captured = slot<NearbySpotSearch>()
        every { spotQueryPort.findReactedSpotIds(7L, DomainLocale.JA) } returns setOf(9L)
        every { spotSpatialQueryPort.searchNearby(capture(captured)) } returns emptyList()

        service.spots(query(), DomainLocale.JA, memberId = 7L)

        assertEquals(setOf(9L), captured.captured.excludedSpotIds)
        verify { spotQueryPort.findReactedSpotIds(7L, DomainLocale.JA) }
    }

    // ── SPEC-0008 좋아요 목록 (R-15 ~ R-17) ──────────────────────────

    private fun likedSpot(id: Long, likedAt: Instant = Instant.parse("2026-08-01T00:00:00Z")) = LikedSpotView(
        id = id,
        title = "spot-$id",
        thumbnailImageUrl = "https://img/$id.jpg",
        address = null,
        category = SpotCategory.TOURIST_SPOT,
        copyright = CopyrightType.TYPE1,
        likedAt = likedAt,
        catalogId = id * 10,
    )

    private fun likedPage(vararg nodes: LikedSpotView) = CursorPage(
        edges = nodes.map { org.around.ieuri.common.pagination.CursorEdge("c-${it.id}", it) },
        pageInfo = CursorPageInfo(
            hasNextPage = false,
            hasPreviousPage = false,
            startCursor = nodes.firstOrNull()?.let { "c-${it.id}" },
            endCursor = nodes.lastOrNull()?.let { "c-${it.id}" },
        ),
        // 좋아요 목록은 총 개수를 노출하지 않는다.
        totalCount = null,
    )

    @Test
    fun `AC-48·AC-49 - 인증 회원과 요청 언어가 조회 조건으로 전달된다`() {
        val captured = slot<LikedSpotsSearch>()
        every { spotQueryPort.findLikedSpots(capture(captured)) } returns likedPage()

        service.likedSpots(LikedSpotsQuery(categories = CategorySelection.UNFILTERED, first = 20, after = null), DomainLocale.EN, memberId = 7L)

        assertEquals(7L, captured.captured.memberId)
        assertEquals(DomainLocale.EN, captured.captured.language)
    }

    @Test
    fun `AC-45 - 포트가 돌려준 목록을 그대로 반환한다`() {
        every { spotQueryPort.findLikedSpots(any()) } returns likedPage(likedSpot(1L), likedSpot(2L))

        val page = service.likedSpots(LikedSpotsQuery(CategorySelection.UNFILTERED, 20, null), DomainLocale.KO, memberId = 7L)

        assertEquals(listOf(1L, 2L), page.edges.map { it.node.id })
    }

    @Test
    fun `AC-52 - first가 상한을 초과하면 클램프된다`() {
        val captured = slot<LikedSpotsSearch>()
        every { spotQueryPort.findLikedSpots(capture(captured)) } returns likedPage()

        service.likedSpots(LikedSpotsQuery(categories = CategorySelection.UNFILTERED, first = 500, after = null), DomainLocale.KO, memberId = 7L)

        assertEquals(100, captured.captured.first)
    }

    @Test
    fun `first가 0 이하이면 최소 1로 보정된다`() {
        val captured = slot<LikedSpotsSearch>()
        every { spotQueryPort.findLikedSpots(capture(captured)) } returns likedPage()

        service.likedSpots(LikedSpotsQuery(categories = CategorySelection.UNFILTERED, first = 0, after = null), DomainLocale.KO, memberId = 7L)

        assertEquals(1, captured.captured.first)
    }

    @Test
    fun `AC-51 - after 커서가 조회 조건으로 전달된다`() {
        val captured = slot<LikedSpotsSearch>()
        every { spotQueryPort.findLikedSpots(capture(captured)) } returns likedPage()

        service.likedSpots(LikedSpotsQuery(CategorySelection.UNFILTERED, 20, after = "cursor-abc"), DomainLocale.KO, memberId = 7L)

        assertEquals("cursor-abc", captured.captured.after)
    }

    @Test
    fun `카테고리 필터가 조회 조건으로 전달된다`() {
        val captured = slot<LikedSpotsSearch>()
        every { spotQueryPort.findLikedSpots(capture(captured)) } returns likedPage()

        service.likedSpots(
            LikedSpotsQuery(categories = CategorySelection.ofValues(setOf(SpotCategory("RESTAURANT"))), first = 20, after = null),
            DomainLocale.KO,
            memberId = 7L,
        )

        assertEquals(CategorySelection.ofValues(setOf(SpotCategory("RESTAURANT"))), captured.captured.categories)
    }

    @Test
    fun `카테고리 미지정이면 전체를 조회한다`() {
        val captured = slot<LikedSpotsSearch>()
        every { spotQueryPort.findLikedSpots(capture(captured)) } returns likedPage()

        service.likedSpots(LikedSpotsQuery(CategorySelection.UNFILTERED, 20, null), DomainLocale.KO, memberId = 7L)

        assertEquals(CategorySelection.UNFILTERED, captured.captured.categories)
    }

    // ── SPEC-0033 좋아요 목록 내 검색: likedSpots는 메인 DB, searchLikedSpots는 검색 노드 ──

    @Test
    fun `AC1_1·AC1_2 - 검색 없는 목록은 메인 DB만 본다`() {
        every { spotQueryPort.findLikedSpots(any()) } returns likedPage()

        service.likedSpots(LikedSpotsQuery(CategorySelection.UNFILTERED, 20, null), DomainLocale.KO, memberId = 7L)

        verify(exactly = 1) { spotQueryPort.findLikedSpots(any()) }
        verify(exactly = 0) { likedSpotSearchPort.searchLikedSpots(any(), any()) }
    }

    @Test
    fun `AC1_3 - 검색은 검색 노드만 본다`() {
        val captured = slot<String>()
        every { likedSpotSearchPort.searchLikedSpots(any(), capture(captured)) } returns likedPage(likedSpot(1L))

        service.searchLikedSpots(
            LikedSpotsQuery(CategorySelection.UNFILTERED, 20, null),
            text = "sushi",
            locale = DomainLocale.KO,
            memberId = 7L,
        )

        assertEquals("sushi", captured.captured)
        verify(exactly = 0) { spotQueryPort.findLikedSpots(any()) }
    }

    @Test
    fun `AC3_1·AC3_2 - 정렬은 포트가 돌려준 순서 그대로다`() {
        // 정렬은 likedAt DESC 고정이다(POL-019·POL-055).
        every { likedSpotSearchPort.searchLikedSpots(any(), any()) } returns
            likedPage(likedSpot(3L), likedSpot(1L), likedSpot(2L))

        val page = service.searchLikedSpots(
            LikedSpotsQuery(CategorySelection.UNFILTERED, 20, null),
            text = "gyeongbok",
            locale = DomainLocale.KO,
            memberId = 7L,
        )

        assertEquals(listOf(3L, 1L, 2L), page.edges.map { it.node.id })
    }

    @Test
    fun `AC4_1~AC4_3 - 커서가 그대로 전달된다`() {
        val captured = slot<LikedSpotsSearch>()
        every { likedSpotSearchPort.searchLikedSpots(capture(captured), any()) } returns likedPage(likedSpot(1L))

        service.searchLikedSpots(
            LikedSpotsQuery(CategorySelection.UNFILTERED, 20, after = "cursor-abc"),
            text = "sushi",
            locale = DomainLocale.KO,
            memberId = 7L,
        )

        assertEquals("cursor-abc", captured.captured.after)
    }

    @Test
    fun `AC5_1·AC5_2 - 검색어와 카테고리가 함께 전달된다`() {
        val captured = slot<LikedSpotsSearch>()
        val text = slot<String>()
        every { likedSpotSearchPort.searchLikedSpots(capture(captured), capture(text)) } returns likedPage()

        service.searchLikedSpots(
            LikedSpotsQuery(
                categories = CategorySelection.ofValues(setOf(SpotCategory("RESTAURANT"))),
                first = 20,
                after = null,
            ),
            text = "sushi",
            locale = DomainLocale.KO,
            memberId = 7L,
        )

        assertEquals(CategorySelection.ofValues(setOf(SpotCategory("RESTAURANT"))), captured.captured.categories)
        assertEquals("sushi", text.captured)
    }

    @Test
    fun `검색에도 인증 회원·언어·페이지 상한이 목록과 같게 걸린다`() {
        val captured = slot<LikedSpotsSearch>()
        every { likedSpotSearchPort.searchLikedSpots(capture(captured), any()) } returns likedPage()

        service.searchLikedSpots(
            LikedSpotsQuery(CategorySelection.UNFILTERED, first = 500, after = null),
            text = "sushi",
            locale = DomainLocale.EN,
            memberId = 7L,
        )

        assertEquals(7L, captured.captured.memberId)
        assertEquals(DomainLocale.EN, captured.captured.language)
        assertEquals(100, captured.captured.first)
    }

    @Test
    fun `후속 페이지도 커서만 실어 같은 포트로 간다`() {
        every { likedSpotSearchPort.searchLikedSpots(any(), any()) } returns likedPage(likedSpot(1L))

        service.searchLikedSpots(
            LikedSpotsQuery(CategorySelection.UNFILTERED, 20, after = "cursor-abc"),
            text = "gyeongbok",
            locale = DomainLocale.KO,
            memberId = 7L,
        )

        verify(exactly = 1) { likedSpotSearchPort.searchLikedSpots(any(), any()) }
        verify(exactly = 0) { spotQueryPort.findLikedSpots(any()) }
    }

    @Test
    fun `AC6_1 - 트림 후 빈 검색어는 EMPTY_QUERY다`() {
        val e = assertFailsWith<EmptyQueryException> {
            service.searchLikedSpots(
                LikedSpotsQuery(CategorySelection.UNFILTERED, 20, null),
                text = "   ",
                locale = DomainLocale.KO,
                memberId = 7L,
            )
        }

        assertEquals("EMPTY_QUERY", e.logicalCode)
        verify(exactly = 0) { likedSpotSearchPort.searchLikedSpots(any(), any()) }
    }

    @Test
    fun `AC6_2 - 트림 후 101자면 QUERY_TOO_LONG이다`() {
        val e = assertFailsWith<QueryTooLongException> {
            service.searchLikedSpots(
                LikedSpotsQuery(CategorySelection.UNFILTERED, 20, null),
                text = "a".repeat(101),
                locale = DomainLocale.KO,
                memberId = 7L,
            )
        }

        assertEquals("QUERY_TOO_LONG", e.logicalCode)
        verify(exactly = 0) { likedSpotSearchPort.searchLikedSpots(any(), any()) }
    }

    @Test
    fun `AC6_3 - 트림 후 정확히 100자면 통과한다`() {
        val captured = slot<String>()
        every { likedSpotSearchPort.searchLikedSpots(any(), capture(captured)) } returns likedPage()
        val text = "a".repeat(100)

        service.searchLikedSpots(
            LikedSpotsQuery(CategorySelection.UNFILTERED, 20, null),
            text = text,
            locale = DomainLocale.KO,
            memberId = 7L,
        )

        assertEquals(text, captured.captured)
    }

    @Test
    fun `AC6_4 - 앞뒤 공백은 제거된 뒤 전달된다`() {
        // 길이도 트림 후를 센다.
        val captured = slot<String>()
        every { likedSpotSearchPort.searchLikedSpots(any(), capture(captured)) } returns likedPage()

        service.searchLikedSpots(
            LikedSpotsQuery(CategorySelection.UNFILTERED, 20, null),
            text = "  ${"a".repeat(100)}  ",
            locale = DomainLocale.KO,
            memberId = 7L,
        )

        assertEquals("a".repeat(100), captured.captured)
    }

    @Test
    fun `AC7_3 - 오류 메시지에 검색어 원문이 남지 않는다`() {
        val secret = "내가 저장한 그 초밥집"
        val e = assertFailsWith<QueryTooLongException> {
            service.searchLikedSpots(
                LikedSpotsQuery(CategorySelection.UNFILTERED, 20, null),
                text = secret.repeat(20),
                locale = DomainLocale.KO,
                memberId = 7L,
            )
        }

        assertFalse(e.message.orEmpty().contains(secret))
    }

    private fun spot(id: Long) = MapSpotView(
        id = id,
        title = "spot$id",
        thumbnailImageUrl = "https://img/$id",
        category = SpotCategory.TOURIST_SPOT,
        lng = 127.0,
        lat = 37.5,
    )

    // ---------- mapViewport ----------

    @Test
    fun `줌과 무관하게 개별 마커를 돌려준다`() {
        every { congestionQueryUseCase.cachedEventsWithin(seoulBounds) } returns emptyList()
        every { mapQueryPort.findSpotsWithin(any()) } returns listOf(spot(1), spot(2))

        for (zoom in listOf(1, 8, 12, 14)) {
            val result = service.mapViewport(
                MapViewportQuery(bounds = seoulBounds, zoom = zoom, categories = CategorySelection.UNFILTERED),
                DomainLocale.KO,
            )

            assertEquals(2, result.spots.size, "zoom=$zoom")
            assertFalse(result.truncated, "zoom=$zoom")
        }
    }

    @Test
    fun `상한을 초과하면 잘리고 truncated가 선다`() {
        // AC R3. 포트는 상한 + 1건을 돌려주고 서비스가 상한까지만 싣는다.
        every { congestionQueryUseCase.cachedEventsWithin(seoulBounds) } returns emptyList()
        every { mapQueryPort.findSpotsWithin(any()) } returns
            listOf(spot(1), spot(2), spot(3), spot(4))

        val result = service.mapViewport(
            MapViewportQuery(bounds = seoulBounds, zoom = 12, categories = CategorySelection.UNFILTERED),
            DomainLocale.KO,
        )

        assertEquals(3, result.spots.size)
        assertTrue(result.truncated)
    }

    @Test
    fun `상한을 넘겨 조회해 잘림을 판정한다`() {
        // AC R3. COUNT(*) 없이 상한 + 1로 판정한다.
        every { congestionQueryUseCase.cachedEventsWithin(seoulBounds) } returns emptyList()
        val search = slot<MapViewportSearch>()
        every { mapQueryPort.findSpotsWithin(capture(search)) } returns emptyList()

        service.mapViewport(
            MapViewportQuery(bounds = seoulBounds, zoom = 12, categories = CategorySelection.UNFILTERED),
            DomainLocale.KO,
        )

        assertEquals(viewportPolicy.maxSpotMarkers + 1, search.captured.limit)
    }

    @Test
    fun `줌이 유효 범위 밖이면 거절된다`() {
        // AC R4.
        assertFailsWith<InvalidZoomException> {
            service.mapViewport(MapViewportQuery(seoulBounds, zoom = 0, categories = CategorySelection.UNFILTERED), DomainLocale.KO)
        }
        assertFailsWith<InvalidZoomException> {
            service.mapViewport(MapViewportQuery(seoulBounds, zoom = 15, categories = CategorySelection.UNFILTERED), DomainLocale.KO)
        }
    }

    @Test
    fun `면적 상한을 초과하면 거절된다`() {
        // AC R5.
        val huge = MapBounds(minLat = 0.0, minLng = 0.0, maxLat = 80.0, maxLng = 170.0)

        assertFailsWith<BboxTooLargeException> {
            service.mapViewport(MapViewportQuery(huge, zoom = 12, categories = CategorySelection.UNFILTERED), DomainLocale.KO)
        }
    }

    @Test
    fun `혼잡도 캐시가 준 문화행사를 싣고 상한을 넘으면 잘린다`() {
        // AC R12. 행사는 congestion이 캐시에서 모은 값이다.
        val events = (1..3).map { CulturalEventView("e$it", "축제$it", "광장", "2026-08-01~2026-08-10", 127.0, 37.5, null) }
        every { congestionQueryUseCase.cachedEventsWithin(seoulBounds) } returns events
        every { mapQueryPort.findSpotsWithin(any()) } returns emptyList()

        val result = service.mapViewport(
            MapViewportQuery(seoulBounds, zoom = 12, categories = CategorySelection.UNFILTERED),
            DomainLocale.KO,
        )

        assertEquals(events.take(viewportPolicy.maxEventMarkers), result.events)
        assertTrue(result.truncated)
    }

    /** 필드마다 다른 값 — 경도·위도가 뒤바뀌면 실패한다. */
    private fun basics() = SpotBasics(
        title = "경복궁",
        address = "서울 종로구",
        thumbnailImageUrl = "https://img/100.jpg",
        category = SpotCategory("TOURIST_SPOT"),
        copyright = CopyrightType.TYPE1,
        longitude = 126.977,
        latitude = 37.579,
    )

    @Test
    fun `spotSummary는 카탈로그 id와 spot 행 값을 필드별로 옮긴다`() {
        every { spotQueryPort.findSpotBasics(100L, DomainLocale.JA) } returns basics()

        val summary = service.spotSummary(100L, "JA")

        assertEquals(100L, summary?.id)
        assertEquals("경복궁", summary?.title)
        assertEquals("https://img/100.jpg", summary?.thumbnailImageUrl)
        assertEquals("TOURIST_SPOT", summary?.categoryCode)
        assertEquals(126.977, summary?.longitude)
        assertEquals(37.579, summary?.latitude)
    }

    @Test
    fun `spotSummary는 그 언어에 없으면 다른 언어로 대신하지 않고 null이다`() {
        every { spotQueryPort.findSpotBasics(100L, DomainLocale.EN) } returns null

        assertNull(service.spotSummary(100L, "EN"))
    }

    @Test
    fun `spotSummaries는 해소된 id만 키로 두고 같은 변환을 쓴다`() {
        every { spotQueryPort.findSpotBasicsIn(listOf(100L, 200L), DomainLocale.KO) } returns mapOf(200L to basics())

        val summaries = service.spotSummaries(listOf(100L, 200L), "KO")

        assertEquals(setOf(200L), summaries.keys)
        assertEquals(200L, summaries[200L]?.id)
        assertEquals(126.977, summaries[200L]?.longitude)
        assertEquals(37.579, summaries[200L]?.latitude)
    }

    @Test
    fun `회원의 주변 조회는 좌표 조회 사실을 남기고, 비로그인은 남기지 않는다`() {
        service.spots(query(), DomainLocale.KO, memberId = 7L)
        service.spots(query(), DomainLocale.KO, memberId = null)

        verify(exactly = 1) { eventPublisher.publish(LocationAccessed(7L, "spots")) }
        verify(exactly = 1) { eventPublisher.publish(any()) }
    }

    // ── 지연 필드: 좋아요 수 ─────────────────────────────────────────

    @Test
    fun `AC-14 - 좋아요 수는 매번 집계한다`() {
        every { spotQueryPort.countLikes(100L, DomainLocale.KO) } returns 3L

        assertEquals(3L, service.spotLikeCount(100L, DomainLocale.KO))
        assertEquals(3L, service.spotLikeCount(100L, DomainLocale.KO))
        verify(exactly = 2) { spotQueryPort.countLikes(100L, DomainLocale.KO) }
    }

    @Test
    fun `AC-12 - 반응이 없으면 좋아요 수는 0`() {
        every { spotQueryPort.countLikes(any(), any()) } returns 0L
        assertEquals(0L, service.spotLikeCount(100L, DomainLocale.KO))
    }

    @Test
    fun `AC-13_5 - 좋아요 수는 활성 언어 안에서만 센다`() {
        every { spotQueryPort.countLikes(100L, DomainLocale.EN) } returns 5L
        every { spotQueryPort.countLikes(100L, DomainLocale.JA) } returns 1L

        assertEquals(5L, service.spotLikeCount(100L, DomainLocale.EN))
        assertEquals(1L, service.spotLikeCount(100L, DomainLocale.JA))
    }

    @Test
    fun `AC-13_2 - 다른 언어에서 누른 반응은 이 언어에 보이지 않는다`() {
        every { spotQueryPort.findReaction(100L, 7L, DomainLocale.EN) } returns ReactionType.LIKE
        every { spotQueryPort.findReaction(100L, 7L, DomainLocale.JA) } returns null

        assertEquals(ReactionType.LIKE, service.spotViewerReaction(100L, 7L, DomainLocale.EN))
        assertNull(service.spotViewerReaction(100L, 7L, DomainLocale.JA))
    }
}
