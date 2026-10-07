package org.around.ieuri.tourism.application.catalog

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.common.event.LocationAccessed
import org.around.ieuri.tourism.application.catalog.provided.dto.FestivalView
import org.around.ieuri.tourism.application.catalog.provided.dto.OngoingFestivalsQuery
import org.around.ieuri.tourism.application.catalog.provided.dto.PopularSpotView
import org.around.ieuri.tourism.application.catalog.provided.dto.PopularSpotsQuery
import org.around.ieuri.tourism.application.catalog.provided.dto.SearchSpotsQuery
import org.around.ieuri.tourism.application.catalog.provided.dto.SpotView
import org.around.ieuri.tourism.application.catalog.provided.exception.InvalidCoordinatesException
import org.around.ieuri.tourism.application.catalog.provided.exception.RadiusTooLargeException
import org.around.ieuri.tourism.application.catalog.required.CatalogEventPublisherPort
import org.around.ieuri.tourism.application.catalog.required.FestivalQueryPort
import org.around.ieuri.tourism.application.catalog.required.OngoingFestivalSearch
import org.around.ieuri.tourism.application.catalog.required.PopularSpotQueryPort
import org.around.ieuri.tourism.application.catalog.required.PopularSpotSearch
import org.around.ieuri.tourism.application.catalog.required.SpotSearchHit
import org.around.ieuri.tourism.application.catalog.required.SpotSearchPort
import org.around.ieuri.tourism.application.catalog.required.SpotSearchQuery
import org.around.ieuri.tourism.application.catalog.required.SpotSearchResult
import org.around.ieuri.tourism.application.catalog.required.SpotSuggestionHit
import org.around.ieuri.tourism.domain.catalog.CategorySelection
import org.around.ieuri.tourism.domain.catalog.DistanceCursor
import org.around.ieuri.tourism.domain.catalog.LikeCountCursor
import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.around.ieuri.tourism.domain.shared.DomainLocale
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * SpotDiscoveryService — SPEC-0013 R1-R9(검색·자동완성), R17(진행 중 축제), R18(인기).
 * 서비스가 포트에 넘기는 조건과 페이지 구성만 본다.
 */
class SpotDiscoveryServiceTest {

    private val spotSearchPort = mockk<SpotSearchPort>()
    private val festivalQueryPort = mockk<FestivalQueryPort>()
    private val popularSpotQueryPort = mockk<PopularSpotQueryPort>()

    /** AC-17.1~17.3의 경계 검증을 고정하기 위해 '오늘'을 2026-08-09로 못 박는다. */
    private val today = LocalDate.of(2026, 8, 9)
    private val clock = Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)

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

    private val eventPublisher = mockk<CatalogEventPublisherPort>(relaxed = true)

    private val service = SpotDiscoveryService(
        spotSearchPort,
        festivalQueryPort,
        popularSpotQueryPort,
        policy,
        clock,
        eventPublisher,
    )

    init {
        every { spotSearchPort.search(any()) } returns emptyList()
        every { spotSearchPort.count(any()) } returns 0
        every { spotSearchPort.suggest(any(), any(), any(), any(), any()) } returns emptyList()
        every { festivalQueryPort.findOngoing(any()) } returns emptyList()
        every { popularSpotQueryPort.findPopular(any()) } returns emptyList()
    }

    // ── R1 · BM25 관련도 검색 ────────────────────────────────────────────────

    /** AC-1.1 — 포트가 매긴 관련도 순서를 서비스가 그대로 보존한다. */
    @Test
    fun `관련도가 높은 순서로 결과가 나온다`() {
        every { spotSearchPort.search(any()) } returns listOf(
            hit(spotId = 1L, title = "경복궁", relevance = 0.9),
            hit(spotId = 2L, title = "경복궁로 카페", relevance = 0.3),
        )

        val result = service.searchSpots(searchQuery(query = "경복궁"), DomainLocale.KO, memberId = null)

        assertEquals(listOf(1L, 2L), result.items.map { it.id })
    }

    /** AC-1.3 — 0건은 오류가 아니다. */
    @Test
    fun `결과가 없어도 오류가 아니라 빈 페이지다`() {
        val result = service.searchSpots(searchQuery(query = "없는장소"), DomainLocale.KO, memberId = null)

        assertTrue(result.success)
        assertTrue(result.items.isEmpty())
        assertEquals(0, result.totalCount)
    }

    /** AC-1.2 — 반경은 포트로 넘어가며 서비스가 임의로 넓히지 않는다. */
    @Test
    fun `요청한 반경이 그대로 포트에 전달된다`() {
        val captured = slot<SpotSearchQuery>()
        every { spotSearchPort.search(capture(captured)) } returns emptyList()

        service.searchSpots(searchQuery(query = "경복궁", radiusMeters = 5_000), DomainLocale.KO, memberId = null)

        assertEquals(5_000, captured.captured.radiusMeters)
    }

    // ── R2 · 언어와 토크나이저 ──────────────────────────────────────────────

    /** AC-2.1 — 질의 문자열이 변형 없이 포트에 도달한다(트림만 한다). */
    @Test
    fun `질의 문자열은 변형 없이 포트로 전달된다`() {
        val captured = slot<SpotSearchQuery>()
        every { spotSearchPort.search(capture(captured)) } returns emptyList()

        service.searchSpots(searchQuery(query = "경복궁 야간개장"), DomainLocale.KO, memberId = null)

        assertEquals("경복궁 야간개장", captured.captured.text)
    }

    /** AC-2.2·AC-2.3 — 요청 언어가 포트로 전달된다. */
    @Test
    fun `요청 언어가 포트로 전달된다`() {
        val captured = slot<SpotSearchQuery>()
        every { spotSearchPort.search(capture(captured)) } returns emptyList()

        service.searchSpots(searchQuery(query = "金閣寺"), DomainLocale.JA, memberId = null)

        assertEquals(DomainLocale.JA, captured.captured.language)
    }

    // ── R5 · 카테고리 필터 ──────────────────────────────────────────────────

    /** AC-5.1 — 지정한 카테고리가 포트 조건으로 넘어간다. */
    @Test
    fun `카테고리를 지정하면 조건으로 전달된다`() {
        val captured = slot<SpotSearchQuery>()
        every { spotSearchPort.search(capture(captured)) } returns emptyList()

        service.searchSpots(
            searchQuery(query = "경복궁", category = SpotCategory.TOURIST_SPOT),
            DomainLocale.KO,
            memberId = null,
        )

        assertEquals(CategorySelection.ofValues(setOf(SpotCategory.TOURIST_SPOT)), captured.captured.categories)
    }

    /** AC-5.2 — 카테고리를 지정하지 않으면 필터가 없다(전체 대상). */
    @Test
    fun `카테고리를 생략하면 필터가 걸리지 않는다`() {
        val captured = slot<SpotSearchQuery>()
        every { spotSearchPort.search(capture(captured)) } returns emptyList()

        service.searchSpots(searchQuery(query = "경복궁"), DomainLocale.KO, memberId = null)

        assertEquals(CategorySelection.UNFILTERED, captured.captured.categories)
    }

    // ── R7 · 안정적 페이지네이션 ────────────────────────────────────────────

    /** AC-7.1·AC-7.2 — 페이지 번호가 오프셋으로 환산되어 포트에 전달된다. */
    @Test
    fun `페이지 번호가 오프셋으로 환산된다`() {
        val captured = slot<SpotSearchQuery>()
        every { spotSearchPort.search(capture(captured)) } returns emptyList()

        service.searchSpots(searchQuery(query = "경복궁", page = 3, size = 20), DomainLocale.KO, memberId = null)

        assertEquals(40, captured.captured.offset)
        assertEquals(20, captured.captured.limit)
    }

    /** 첫 페이지의 오프셋은 0이다. */
    @Test
    fun `첫 페이지의 오프셋은 0이다`() {
        val captured = slot<SpotSearchQuery>()
        every { spotSearchPort.search(capture(captured)) } returns emptyList()

        service.searchSpots(searchQuery(query = "경복궁"), DomainLocale.KO, memberId = null)

        assertEquals(0, captured.captured.offset)
    }

    /** 페이지 상한을 넘으면 PAGE_TOO_DEEP이고 포트를 호출하지 않는다. */
    @Test
    fun `페이지가 너무 깊으면 PAGE_TOO_DEEP이다`() {
        val result = service.searchSpots(searchQuery(query = "경복궁", page = 501), DomainLocale.KO, memberId = null)

        assertFalse(result.success)
        assertEquals(listOf("PAGE_TOO_DEEP"), result.userErrors.map { it.code })
        verify(exactly = 0) { spotSearchPort.search(any()) }
    }

    /** AC-7.3 — 이 페이지까지로 전체 건수가 소진되면 마지막 페이지다. */
    @Test
    fun `마지막 페이지에서 hasNext가 false다`() {
        every { spotSearchPort.search(any()) } returns listOf(hit(spotId = 1L))
        every { spotSearchPort.count(any()) } returns 1

        val result = service.searchSpots(searchQuery(query = "경복궁", size = 20), DomainLocale.KO, memberId = null)

        assertFalse(result.hasNext)
    }

    /** AC-7.3 — 전체 건수가 남아 있으면 다음 페이지가 있다. hasNext는 totalCount에서 도출된다. */
    @Test
    fun `전체 건수가 남아 있으면 hasNext가 true다`() {
        every { spotSearchPort.search(any()) } returns (1..2L).map { hit(spotId = it) }
        every { spotSearchPort.count(any()) } returns 5

        val result = service.searchSpots(searchQuery(query = "경복궁", size = 2), DomainLocale.KO, memberId = null)

        assertTrue(result.hasNext)
        assertEquals(listOf(1L, 2L), result.items.map { it.id })
        assertEquals(5, result.totalCount)
    }

    /** 마지막 페이지 경계 — 2페이지에서 4건을 소진하면 다음이 없다. */
    @Test
    fun `마지막 페이지 경계에서 hasNext가 false다`() {
        every { spotSearchPort.search(any()) } returns (3..4L).map { hit(spotId = it) }
        every { spotSearchPort.count(any()) } returns 4

        val result = service.searchSpots(searchQuery(query = "경복궁", page = 2, size = 2), DomainLocale.KO, memberId = null)

        assertFalse(result.hasNext)
    }

    /** POL-018 — 상한 초과는 거절이 아니라 클램프다. */
    @Test
    fun `size가 상한을 넘으면 클램프된다`() {
        val captured = slot<SpotSearchQuery>()
        every { spotSearchPort.search(capture(captured)) } returns emptyList()

        service.searchSpots(searchQuery(query = "경복궁", size = 999), DomainLocale.KO, memberId = null)

        assertEquals(50, captured.captured.limit)
    }

    // ── R8 · 빈 질의 거절 ───────────────────────────────────────────────────

    /** AC-8.1 — 빈 질의는 EMPTY_QUERY이고 **검색 포트를 호출하지 않는다.** */
    @Test
    fun `빈 질의는 EMPTY_QUERY이고 포트를 호출하지 않는다`() {
        val result = service.searchSpots(searchQuery(query = ""), DomainLocale.KO, memberId = null)

        assertFalse(result.success)
        assertEquals(listOf("EMPTY_QUERY"), result.userErrors.map { it.code })
        assertTrue(result.items.isEmpty())
        verify(exactly = 0) { spotSearchPort.search(any()) }
        verify(exactly = 0) { spotSearchPort.count(any()) }
    }

    /** AC-8.2 — 공백뿐인 질의도 트림 후 EMPTY_QUERY다. */
    @Test
    fun `공백뿐인 질의는 트림 후 EMPTY_QUERY다`() {
        val result = service.searchSpots(searchQuery(query = "   \t \n "), DomainLocale.KO, memberId = null)

        assertEquals(listOf("EMPTY_QUERY"), result.userErrors.map { it.code })
        verify(exactly = 0) { spotSearchPort.search(any()) }
    }

    /** AC-8.3 — 100자 초과는 QUERY_TOO_LONG. */
    @Test
    fun `100자를 초과하는 질의는 QUERY_TOO_LONG이다`() {
        val result = service.searchSpots(searchQuery(query = "가".repeat(101)), DomainLocale.KO, memberId = null)

        assertFalse(result.success)
        assertEquals(listOf("QUERY_TOO_LONG"), result.userErrors.map { it.code })
        verify(exactly = 0) { spotSearchPort.search(any()) }
    }

    /** 경계 — 정확히 100자는 통과한다. */
    @Test
    fun `정확히 100자인 질의는 통과한다`() {
        val result = service.searchSpots(searchQuery(query = "가".repeat(100)), DomainLocale.KO, memberId = null)

        assertTrue(result.success)
        verify(exactly = 1) { spotSearchPort.search(any()) }
    }

    /** 길이는 트림 후를 센다. */
    @Test
    fun `길이 상한은 트림 후 기준이다`() {
        val padded = "  " + "가".repeat(100) + "  "

        assertTrue(service.searchSpots(searchQuery(query = padded), DomainLocale.KO, memberId = null).success)
    }

    /** AC-4.3 — 한 글자 질의도 거절되지 않는다. */
    @Test
    fun `한 글자 질의도 검색이 수행된다`() {
        val result = service.searchSpots(searchQuery(query = "궁"), DomainLocale.KO, memberId = null)

        assertTrue(result.success)
        verify(exactly = 1) { spotSearchPort.search(any()) }
    }

    /** 반경 오류는 사용자 입력 오류가 아니라 최상위 errors[]다 — `spots`와 같은 규약. */
    @Test
    fun `반경 상한을 넘으면 예외가 전파된다`() {
        assertFailsWith<RadiusTooLargeException> {
            service.searchSpots(searchQuery(query = "경복궁", radiusMeters = 100_001), DomainLocale.KO, memberId = null)
        }
    }

    // ── R9 · 자동완성 ───────────────────────────────────────────────────────

    /** AC-9.1 — 접두사가 포트로 전달되고 결과가 그대로 매핑된다. */
    @Test
    fun `접두사로 시작하는 제목을 반환한다`() {
        every { spotSearchPort.suggest(any(), any(), any(), any(), any()) } returns listOf(
            SpotSuggestionHit(1L, "경복궁", SpotCategory.TOURIST_SPOT),
            SpotSuggestionHit(2L, "경복궁 수문장 교대식", SpotCategory.FESTIVAL_EVENT),
        )

        val result = service.spotSearchSuggestions("경복", 37.5, 127.0, 20_000, 10, DomainLocale.KO, memberId = null)

        assertEquals(listOf("경복궁", "경복궁 수문장 교대식"), result.map { it.title })
        assertEquals(listOf(1L, 2L), result.map { it.id })
    }

    /** AC-9.2 — 상한은 10건이며 초과 요청은 클램프된다. */
    @Test
    fun `자동완성 개수는 10건으로 클램프된다`() {
        val captured = slot<Int>()
        every {
            spotSearchPort.suggest(any(), any(), any(), any(), capture(captured))
        } returns emptyList()

        service.spotSearchSuggestions("경복", 37.5, 127.0, 20_000, 100, DomainLocale.KO, memberId = null)

        assertEquals(10, captured.captured)
    }

    /** AC-9.4 — 빈 질의는 오류가 아니라 빈 목록이고, 포트를 호출하지 않는다. */
    @Test
    fun `자동완성의 빈 질의는 빈 목록이고 포트를 호출하지 않는다`() {
        val result = service.spotSearchSuggestions("  ", 37.5, 127.0, 20_000, 10, DomainLocale.KO, memberId = null)

        assertTrue(result.isEmpty())
        verify(exactly = 0) { spotSearchPort.suggest(any(), any(), any(), any(), any()) }
    }

    /** AC-9.3 — 반경이 포트로 전달된다(반경 밖 제외는 어댑터가 수행). */
    @Test
    fun `자동완성도 반경을 포트로 전달한다`() {
        val captured = slot<Int>()
        every {
            spotSearchPort.suggest(any(), any(), any(), capture(captured), any())
        } returns emptyList()

        service.spotSearchSuggestions("경복", 37.5, 127.0, 3_000, 10, DomainLocale.KO, memberId = null)

        assertEquals(3_000, captured.captured)
    }

    // ── R17 · 진행 중 축제 ──────────────────────────────────────────────────

    /** AC-17.1 — '오늘'은 시계에서 오며 포트의 기준일로 넘어간다. */
    @Test
    fun `진행 중 판정 기준일은 시계에서 온다`() {
        val captured = slot<OngoingFestivalSearch>()
        every { festivalQueryPort.findOngoing(capture(captured)) } returns emptyList()

        service.ongoingFestivals(festivalQuery(), DomainLocale.KO, memberId = null)

        assertEquals(today, captured.captured.asOf)
    }

    /** AC-17.4 — 0건은 오류가 아니다. */
    @Test
    fun `진행 중 축제가 없어도 오류가 아니라 빈 결과다`() {
        val page = service.ongoingFestivals(festivalQuery(), DomainLocale.KO, memberId = null)

        assertTrue(page.edges.isEmpty())
        assertFalse(page.pageInfo.hasNextPage)
    }

    /** 포트가 돌려준 순서(거리 오름차순)를 서비스가 보존한다. */
    @Test
    fun `축제는 포트가 준 순서를 보존한다`() {
        every { festivalQueryPort.findOngoing(any()) } returns listOf(
            festival(id = 1L, distanceMeters = 500.0),
            festival(id = 2L, distanceMeters = 5_000.0),
        )

        val page = service.ongoingFestivals(festivalQuery(), DomainLocale.KO, memberId = null)

        assertEquals(listOf(1L, 2L), page.edges.map { it.node.id })
    }

    /** 축제도 한 건 더 읽어 다음 페이지를 판정한다. */
    @Test
    fun `축제도 요청보다 한 건 더 오면 hasNextPage가 true다`() {
        every { festivalQueryPort.findOngoing(any()) } returns (1..3L).map { festival(id = it) }

        val page = service.ongoingFestivals(festivalQuery(first = 2), DomainLocale.KO, memberId = null)

        assertTrue(page.pageInfo.hasNextPage)
        assertEquals(listOf(1L, 2L), page.edges.map { it.node.id })
    }

    // ── R18 · 인기 ─────────────────────────────────────────────────────────

    /** AC-18.1 — 포트가 준 좋아요 내림차순을 보존하고 likeCount를 함께 싣는다. */
    @Test
    fun `인기 목록은 좋아요 수와 함께 반환된다`() {
        every { popularSpotQueryPort.findPopular(any()) } returns listOf(
            popular(spotId = 1L, likeCount = 30L),
            popular(spotId = 2L, likeCount = 12L),
        )

        val page = service.popularSpots(popularQuery(), DomainLocale.KO, memberId = null)

        assertEquals(listOf(1L, 2L), page.edges.map { it.node.spot.id })
        assertEquals(listOf(30L, 12L), page.edges.map { it.node.likeCount })
    }

    /** AC-18.2 — 좋아요 하한은 정책값이며 포트 조건으로 넘어간다. */
    @Test
    fun `좋아요 하한 10이 포트로 전달된다`() {
        val captured = slot<PopularSpotSearch>()
        every { popularSpotQueryPort.findPopular(capture(captured)) } returns emptyList()

        service.popularSpots(popularQuery(), DomainLocale.KO, memberId = null)

        assertEquals(10, captured.captured.minLikeCount)
    }

    /** AC-18.4 — 조건을 만족하는 spot이 없으면 빈 결과가 정상이다. */
    @Test
    fun `인기 조건을 만족하는 spot이 없으면 빈 결과다`() {
        val page = service.popularSpots(popularQuery(), DomainLocale.KO, memberId = null)

        assertTrue(page.edges.isEmpty())
    }

    /** AC-18.3 — 커서가 (좋아요 수, id)라 동점에서도 페이지 간 중복이 없다. */
    @Test
    fun `인기 커서는 좋아요 수와 id를 함께 인코딩한다`() {
        every { popularSpotQueryPort.findPopular(any()) } returns listOf(
            popular(spotId = 1L, likeCount = 12L),
            popular(spotId = 2L, likeCount = 12L),
        )

        val page = service.popularSpots(popularQuery(), DomainLocale.KO, memberId = null)

        val decoded = page.edges.map {
            LikeCountCursor.decode(it.cursor)
        }
        assertEquals(listOf(1L, 2L), decoded.map { it.spotId })
        assertEquals(listOf(12L, 12L), decoded.map { it.likeCount })
    }

    /** 카테고리 필터는 인기 목록에도 적용된다. */
    @Test
    fun `인기 목록도 카테고리를 포트로 전달한다`() {
        val captured = slot<PopularSpotSearch>()
        every { popularSpotQueryPort.findPopular(capture(captured)) } returns emptyList()

        service.popularSpots(popularQuery(category = SpotCategory.TOURIST_SPOT), DomainLocale.KO, memberId = null)

        assertEquals(CategorySelection.ofValues(setOf(SpotCategory.TOURIST_SPOT)), captured.captured.categories)
    }

    // ── 네 진입점 공통 규약 ─────────────────────────────────────────────────

    /** 네 조회가 모두 같은 좌표·반경 검증을 거친다. */
    @Test
    fun `네 조회 모두 반경 상한을 넘으면 거절한다`() {
        assertFailsWith<RadiusTooLargeException> {
            service.searchSpots(searchQuery(query = "경복궁", radiusMeters = 100_001), DomainLocale.KO, memberId = null)
        }
        assertFailsWith<RadiusTooLargeException> {
            service.spotSearchSuggestions("경복", 37.5, 127.0, 100_001, 10, DomainLocale.KO, memberId = null)
        }
        assertFailsWith<RadiusTooLargeException> {
            service.ongoingFestivals(festivalQuery(radiusMeters = 100_001), DomainLocale.KO, memberId = null)
        }
        assertFailsWith<RadiusTooLargeException> {
            service.popularSpots(popularQuery(radiusMeters = 100_001), DomainLocale.KO, memberId = null)
        }
    }

    @Test
    fun `네 조회 모두 좌표 범위를 벗어나면 거절한다`() {
        assertFailsWith<InvalidCoordinatesException> {
            service.searchSpots(searchQuery(query = "경복궁", latitude = 91.0), DomainLocale.KO, memberId = null)
        }
        assertFailsWith<InvalidCoordinatesException> {
            service.spotSearchSuggestions("경복", 91.0, 127.0, 20_000, 10, DomainLocale.KO, memberId = null)
        }
        assertFailsWith<InvalidCoordinatesException> {
            service.ongoingFestivals(festivalQuery(latitude = 91.0), DomainLocale.KO, memberId = null)
        }
        assertFailsWith<InvalidCoordinatesException> {
            service.popularSpots(popularQuery(latitude = 91.0), DomainLocale.KO, memberId = null)
        }
    }

    /** 커서를 넘겨 받은 페이지는 앞 페이지가 있다 — 커서 커넥션 2종 공통이다. */
    @Test
    fun `after를 주면 hasPreviousPage가 true다`() {
        val festivals = service.ongoingFestivals(
            festivalQuery(after = DistanceCursor(100.0, 1L).encode()),
            DomainLocale.KO,
            memberId = null,
        )
        val popular = service.popularSpots(
            popularQuery(after = LikeCountCursor(12L, 1L).encode()),
            DomainLocale.KO,
            memberId = null,
        )

        assertTrue(festivals.pageInfo.hasPreviousPage)
        assertTrue(popular.pageInfo.hasPreviousPage)
    }

    /** 축제·인기의 after도 디코딩되어 포트의 seek 조건으로 넘어간다. */
    @Test
    fun `축제와 인기의 after 커서도 디코딩되어 포트로 전달된다`() {
        val festivalSearch = slot<OngoingFestivalSearch>()
        every { festivalQueryPort.findOngoing(capture(festivalSearch)) } returns emptyList()
        val popularSearch = slot<PopularSpotSearch>()
        every { popularSpotQueryPort.findPopular(capture(popularSearch)) } returns emptyList()

        service.ongoingFestivals(
            festivalQuery(after = DistanceCursor(1234.5, 7L).encode()),
            DomainLocale.KO,
            memberId = null,
        )
        service.popularSpots(
            popularQuery(after = LikeCountCursor(99L, 3L).encode()),
            DomainLocale.KO,
            memberId = null,
        )

        assertEquals(DistanceCursor(1234.5, 7L), festivalSearch.captured.after)
        assertEquals(LikeCountCursor(99L, 3L), popularSearch.captured.after)
    }

    /** 다른 커넥션의 커서를 넘기면 조용히 엉뚱한 위치로 뛰지 않고 거절된다. */
    @Test
    fun `종류가 다른 커서는 거절된다`() {
        assertFailsWith<IllegalArgumentException> {
            service.ongoingFestivals(
                festivalQuery(after = LikeCountCursor(1L, 1L).encode()),
                DomainLocale.KO,
                memberId = null,
            )
        }
    }

    /** first가 0이나 음수여도 최소 1로 올린다 — 스키마가 임의의 Int를 허용한다. */
    @Test
    fun `size가 0 이하이면 1로 올린다`() {
        val captured = slot<SpotSearchQuery>()
        every { spotSearchPort.search(capture(captured)) } returns emptyList()

        service.searchSpots(searchQuery(query = "경복궁", size = 0), DomainLocale.KO, memberId = null)

        assertEquals(1, captured.captured.limit)
    }

    // ── 픽스처 ─────────────────────────────────────────────────────────────

    private fun searchQuery(
        query: String,
        radiusMeters: Int = 20_000,
        category: SpotCategory? = null,
        size: Int = 20,
        page: Int = 1,
        latitude: Double = 37.5,
    ) = SearchSpotsQuery(
        query = query,
        latitude = latitude,
        longitude = 127.0,
        radiusMeters = radiusMeters,
        categories = selection(category),
        page = page,
        size = size,
    )

    private fun festivalQuery(
        first: Int = 20,
        after: String? = null,
        radiusMeters: Int = 20_000,
        latitude: Double = 37.5,
    ) = OngoingFestivalsQuery(
        latitude = latitude,
        longitude = 127.0,
        radiusMeters = radiusMeters,
        first = first,
        after = after,
    )

    private fun popularQuery(
        category: SpotCategory? = null,
        first: Int = 20,
        after: String? = null,
        radiusMeters: Int = 20_000,
        latitude: Double = 37.5,
    ) = PopularSpotsQuery(
        latitude = latitude,
        longitude = 127.0,
        radiusMeters = radiusMeters,
        categories = selection(category),
        first = first,
        after = after,
    )

    /** 단일 카테고리 인자를 다중 선택으로 옮긴다. null이면 필터 없음. */
    private fun selection(category: SpotCategory?) = category?.let { CategorySelection.ofValues(setOf(it)) } ?: CategorySelection.UNFILTERED

    private fun hit(spotId: Long, title: String = "장소$spotId", relevance: Double = 1.0) = SpotSearchHit(
        spot = SpotSearchResult(
            spotId = spotId,
            title = title,
            address = "서울시 종로구",
            thumbnailImageUrl = "https://img/$spotId.jpg",
            mapX = 126.9,
            mapY = 37.5,
            category = SpotCategory.TOURIST_SPOT,
            copyright = CopyrightType.TYPE1,
            distanceMeters = 100.0,
        ),
        relevance = relevance,
    )

    private fun festival(id: Long, distanceMeters: Double = 100.0) = FestivalView(
        id = id,
        title = "축제$id",
        address = "서울시 종로구",
        thumbnailImageUrl = "https://img/$id.jpg",
        distanceMeters = distanceMeters,
        category = SpotCategory.FESTIVAL_EVENT,
        copyright = CopyrightType.TYPE1,
        attributionText = CopyrightType.TYPE1.attributionText,
        mapX = 126.9,
        mapY = 37.5,
        eventStartDate = today.minusDays(1),
        eventEndDate = today.plusDays(1),
    )

    private fun popular(spotId: Long, likeCount: Long) = PopularSpotView(
        spot = SpotView(
            id = spotId,
            title = "장소$spotId",
            address = "서울시 종로구",
            thumbnailImageUrl = "https://img/$spotId.jpg",
            distanceMeters = 100.0,
            category = SpotCategory.TOURIST_SPOT,
            copyright = CopyrightType.TYPE1,
            attributionText = CopyrightType.TYPE1.attributionText,
            mapX = 126.9,
            mapY = 37.5,
        ),
        likeCount = likeCount,
    )

    @Test
    fun `회원의 발견 조회는 연산 이름으로 좌표 조회 사실을 남긴다`() {
        service.searchSpots(searchQuery(query = "경복궁"), DomainLocale.KO, memberId = 7L)
        service.spotSearchSuggestions("경복", 37.5, 127.0, 20_000, 10, DomainLocale.KO, memberId = 7L)
        service.ongoingFestivals(festivalQuery(), DomainLocale.KO, memberId = 7L)
        service.popularSpots(popularQuery(), DomainLocale.KO, memberId = 7L)

        verify(exactly = 1) { eventPublisher.publish(LocationAccessed(7L, "searchSpots")) }
        verify(exactly = 1) { eventPublisher.publish(LocationAccessed(7L, "spotSearchSuggestions")) }
        verify(exactly = 1) { eventPublisher.publish(LocationAccessed(7L, "ongoingFestivals")) }
        verify(exactly = 1) { eventPublisher.publish(LocationAccessed(7L, "popularSpots")) }
    }

    @Test
    fun `입력이 잘못돼도 조회 사실은 먼저 남는다`() {
        assertFailsWith<RadiusTooLargeException> {
            service.ongoingFestivals(festivalQuery(radiusMeters = 100_001), DomainLocale.KO, memberId = 7L)
        }

        verify(exactly = 1) { eventPublisher.publish(LocationAccessed(7L, "ongoingFestivals")) }
    }

    @Test
    fun `비로그인 발견 조회는 아무것도 발행하지 않는다`() {
        service.searchSpots(searchQuery(query = "경복궁"), DomainLocale.KO, memberId = null)
        service.popularSpots(popularQuery(), DomainLocale.KO, memberId = null)

        verify(exactly = 0) { eventPublisher.publish(any()) }
    }
}
