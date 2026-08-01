package org.around.ieuri.tourism.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.tourism.application.exception.CategoryNotAvailableForLocaleException
import org.around.ieuri.tourism.application.exception.InvalidCoordinatesException
import org.around.ieuri.tourism.application.exception.RadiusTooLargeException
import org.around.ieuri.tourism.application.exception.SpotDetailNotFoundException
import org.around.ieuri.tourism.application.policy.SpotQueryPolicy
import org.around.ieuri.tourism.application.port.out.client.NearbySpotData
import org.around.ieuri.tourism.application.port.out.client.NearbySpotQuery
import org.around.ieuri.tourism.application.port.out.client.NearbySpotResult
import org.around.ieuri.tourism.application.port.out.cache.CachedIntro
import org.around.ieuri.tourism.application.port.out.cache.SpotCachePort
import org.around.ieuri.tourism.application.port.out.client.SpotDetailQuery
import org.around.ieuri.tourism.application.port.out.client.TourInfoPort
import org.around.ieuri.tourism.application.port.out.query.SpotQueryPort
import org.around.ieuri.tourism.application.query.SpotsQuery
import org.around.ieuri.tourism.domain.aggregate.SpotDetail
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.domain.vo.SpotImage
import org.around.ieuri.tourism.domain.vo.SpotInfoItem
import org.around.ieuri.tourism.domain.vo.SpotIntro
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** SpotQueryService — DTO→Domain 변환·거리계산·정렬(AC-1/2), 오프셋 페이지, 검증(AC-6/9), 가용성(AC-8). */
class SpotQueryServiceTest {

    private val tourInfoPort = mockk<TourInfoPort>()
    private val spotQueryPort = mockk<SpotQueryPort>()
    private val spotCachePort = mockk<SpotCachePort>()
    private val policy = SpotQueryPolicy(
        maxRadiusMeters = 100_000,
        maxPageSize = 100,
    )
    private val service = SpotQueryService(tourInfoPort, spotQueryPort, spotCachePort, policy)

    // 기본: 아무것도 반응하지 않음(빈 집합). 개별 테스트에서 override.
    init {
        every { spotQueryPort.findReactedContentIds(any(), any(), any()) } returns emptySet()
        // 기본: 캐시 미스 + 저장 성공. 캐시 히트 검증은 개별 테스트에서 override.
        every { spotCachePort.getDetail(any(), any()) } returns null
        every { spotCachePort.putDetail(any(), any(), any()) } returns Unit
        every { spotCachePort.getGallery(any(), any()) } returns null
        every { spotCachePort.putGallery(any(), any(), any()) } returns Unit
        every { spotCachePort.getIntro(any(), any(), any()) } returns null
        every { spotCachePort.putIntro(any(), any(), any(), any()) } returns Unit
        every { spotCachePort.getInfoItems(any(), any(), any()) } returns null
        every { spotCachePort.putInfoItems(any(), any(), any(), any()) } returns Unit
    }

    /**
     * 중립 DTO 픽스처. [rank]가 커질수록 center에서 경도 offset이 커져 거리도 단조 증가한다
     * (서비스가 Haversine으로 거리를 계산하므로, 순서 검증엔 좌표로 거리 순서를 만든다).
     */
    private fun data(id: String, rank: Int): NearbySpotData = NearbySpotData(
        id = id,
        title = "spot-$id",
        address = "서울 어딘가",
        thumbnailImageUrl = "https://img/$id.jpg",
        coordinates = Coordinates(latitude = 37.5665, longitude = 126.9780 + rank * 0.01),
        category = SpotCategory.TOURIST_SPOT,
        copyright = CopyrightType.UNSPECIFIED,
    )

    private fun query(page: Int = 1, size: Int = 100, category: SpotCategory? = null, radius: Int = 20000) =
        SpotsQuery(
            latitude = 37.5665, longitude = 126.9780,
            radiusMeters = radius, category = category, page = page, size = size,
        )

    @Test
    fun `결과는 거리 오름차순으로 정렬된다(AC-1)`() {
        // upstream이 거리순이 아닌 순서로 준다(rank가 클수록 멀다: a<b<c).
        every { tourInfoPort.findNearby(any()) } returns NearbySpotResult(
            spots = listOf(data("c", 3), data("a", 1), data("b", 2)),
            totalCount = 3,
            hasMoreUpstreamPages = false,
        )
        val result = service.spots(query(), DomainLocale.KO, memberId = null)
        assertEquals(listOf("a", "b", "c"), result.items.map { it.id })
    }

    @Test
    fun `page와 size를 upstream pageNo·numOfRows로 전달한다`() {
        val captured = slot<NearbySpotQuery>()
        every { tourInfoPort.findNearby(capture(captured)) } returns NearbySpotResult(emptyList(), 0, false)
        service.spots(query(page = 3, size = 50), DomainLocale.KO, memberId = null)
        assertEquals(3, captured.captured.pageNo)
        assertEquals(50, captured.captured.numOfRows)
    }

    @Test
    fun `hasNext는 upstream 다음 페이지 존재 여부를 그대로 반영한다`() {
        every { tourInfoPort.findNearby(any()) } returns NearbySpotResult(
            spots = listOf(data("a", 1)),
            totalCount = 200,
            hasMoreUpstreamPages = true,
        )
        val result = service.spots(query(page = 1), DomainLocale.KO, memberId = null)
        assertTrue(result.hasNext)
        assertEquals(1, result.page)
        assertEquals(200, result.totalCount) // 참고용 상한(필터 이전)
    }

    @Test
    fun `distanceMeters가 서버 Haversine 계산으로 채워진다(AC-2)`() {
        // center(126.9780)에서 경도 +0.01 떨어진 지점 → 위도 37.5665에서 약 880m.
        every { tourInfoPort.findNearby(any()) } returns NearbySpotResult(
            spots = listOf(data("a", 1)),
            totalCount = 1,
            hasMoreUpstreamPages = false,
        )
        val d = service.spots(query(), DomainLocale.KO, memberId = null).items.single().distanceMeters
        assertTrue(d in 700.0..1_100.0, "expected ~880m, got ${d}m")
    }

    @Test
    fun `size가 상한(100)을 넘으면 클램프된다`() {
        val captured = slot<NearbySpotQuery>()
        every { tourInfoPort.findNearby(capture(captured)) } returns NearbySpotResult(emptyList(), 0, false)
        service.spots(query(size = 500), DomainLocale.KO, memberId = null)
        assertEquals(100, captured.captured.numOfRows)
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
                SpotsQuery(latitude = 999.0, longitude = 0.0, radiusMeters = 20000, category = null, page = 1, size = 100),
                DomainLocale.EN,
                memberId = null,
            )
        }
    }

    @Test
    fun `현재 언어에 없는 카테고리는 CATEGORY_NOT_AVAILABLE_FOR_LOCALE`() {
        assertFailsWith<CategoryNotAvailableForLocaleException> {
            service.spots(query(category = SpotCategory.TRAVEL_COURSE), DomainLocale.EN, memberId = null) // 여행코스는 외국어 없음
        }
    }

    @Test
    fun `카테고리 미지정이면 upstream에 contentTypeId 없이 전달된다`() {
        val captured = slot<NearbySpotQuery>()
        every { tourInfoPort.findNearby(capture(captured)) } returns NearbySpotResult(emptyList(), 0, false)
        service.spots(query(category = null), DomainLocale.KO, memberId = null)
        assertEquals(null, captured.captured.category)
    }

    @Test
    fun `로그인 회원이 이미 반응한 관광지는 제외된다`() {
        every { tourInfoPort.findNearby(any()) } returns NearbySpotResult(
            spots = listOf(data("a", 1), data("b", 2), data("c", 3)),
            totalCount = 3,
            hasMoreUpstreamPages = false,
        )
        // b는 이미 반응함 → 제외.
        every { spotQueryPort.findReactedContentIds(7L, any(), DomainLocale.KO) } returns setOf("b")

        val result = service.spots(query(), DomainLocale.KO, memberId = 7L)
        assertEquals(listOf("a", "c"), result.items.map { it.id })
    }

    @Test
    fun `비로그인은 반응 조회 없이 전부 반환한다`() {
        every { tourInfoPort.findNearby(any()) } returns NearbySpotResult(
            spots = listOf(data("a", 1), data("b", 2)),
            totalCount = 2,
            hasMoreUpstreamPages = false,
        )
        val result = service.spots(query(), DomainLocale.KO, memberId = null)
        assertEquals(listOf("a", "b"), result.items.map { it.id })
        verify(exactly = 0) { spotQueryPort.findReactedContentIds(any(), any(), any()) }
    }

    @Test
    fun `availableCategories는 언어별 유효 집합을 반환한다`() {
        assertTrue(SpotCategory.TRAVEL_COURSE in service.availableCategories(DomainLocale.KO))
        assertTrue(SpotCategory.TRANSPORT in service.availableCategories(DomainLocale.EN))
    }

    // ── spotDetail(SPEC-0003) ───────────────────────────────────────

    private fun detail(likeCount: Long = 0) = SpotDetail(
        id = "100",
        title = "경복궁",
        category = SpotCategory.TOURIST_SPOT,
        thumbnailImageUrl = "https://img/100.jpg",
        overview = "조선의 법궁",
        address = "서울 종로구",
        homepage = null,
        tel = null,
        copyright = CopyrightType.TYPE1,
        likeCount = likeCount,
    )

    private fun intro() = SpotIntro(
        businessHours = "09:00~18:00", closedDays = "화요일", parking = "가능",
        inquiryPhone = null, fee = null, petAllowed = null, creditCardAccepted = null,
    )

    @Test
    fun `spotDetail은 본문만 반환한다(AC-1)`() {
        every { tourInfoPort.findDetail(any()) } returns detail()

        val view = service.spotDetail("100", DomainLocale.KO)

        assertEquals("100", view.id)
        assertEquals("조선의 법궁", view.overview)
        assertEquals(CopyrightType.TYPE1.attributionText, view.attributionText)
    }

    @Test
    fun `본문만 요청하면 갤러리·운영정보·이용안내·좋아요를 조회하지 않는다`() {
        every { tourInfoPort.findDetail(any()) } returns detail()

        service.spotDetail("100", DomainLocale.KO)

        // GraphQL 필드별 지연 조회의 핵심 — 안 물어본 건 upstream/DB를 건드리지 않는다.
        verify(exactly = 0) { tourInfoPort.findGallery(any()) }
        verify(exactly = 0) { tourInfoPort.findIntro(any(), any()) }
        verify(exactly = 0) { tourInfoPort.findInfoItems(any(), any()) }
        verify(exactly = 0) { spotQueryPort.countLikes(any(), any()) }
    }

    @Test
    fun `AC-19 - 본문 캐시 히트면 upstream을 호출하지 않는다`() {
        every { spotCachePort.getDetail("100", DomainLocale.KO) } returns detail()

        service.spotDetail("100", DomainLocale.KO)

        verify(exactly = 0) { tourInfoPort.findDetail(any()) }
        verify(exactly = 0) { spotCachePort.putDetail(any(), any(), any()) }
    }

    @Test
    fun `AC-18 - 본문 캐시 미스면 upstream 조회 후 저장한다`() {
        val fetched = detail()
        every { spotCachePort.getDetail("100", DomainLocale.KO) } returns null
        every { tourInfoPort.findDetail(any()) } returns fetched

        service.spotDetail("100", DomainLocale.KO)

        verify(exactly = 1) { tourInfoPort.findDetail(any()) }
        verify(exactly = 1) { spotCachePort.putDetail("100", DomainLocale.KO, fetched) }
    }

    @Test
    fun `upstream이 실패하면 캐시에 저장하지 않는다(에러 비캐시)`() {
        every { spotCachePort.getDetail(any(), any()) } returns null
        every { tourInfoPort.findDetail(any()) } throws SpotDetailNotFoundException()

        assertFailsWith<SpotDetailNotFoundException> { service.spotDetail("404", DomainLocale.KO) }
        verify(exactly = 0) { spotCachePort.putDetail(any(), any(), any()) }
    }

    @Test
    fun `contentId와 locale을 그대로 포트에 전달한다`() {
        val captured = slot<SpotDetailQuery>()
        every { tourInfoPort.findDetail(capture(captured)) } returns detail()

        service.spotDetail("777", DomainLocale.JA)

        assertEquals("777", captured.captured.contentId)
        assertEquals(DomainLocale.JA, captured.captured.locale)
    }

    // ── 지연 필드: 갤러리 ────────────────────────────────────────────

    @Test
    fun `갤러리는 캐시 미스 시 조회 후 저장한다`() {
        val gallery = listOf(SpotImage("https://img/g1.jpg", null, "야경", CopyrightType.TYPE3))
        every { spotCachePort.getGallery("100", DomainLocale.KO) } returns null
        every { tourInfoPort.findGallery(any()) } returns gallery

        val view = service.spotGallery("100", DomainLocale.KO)

        assertEquals(1, view.size)
        assertEquals(CopyrightType.TYPE3.attributionText, view.single().attributionText)
        verify(exactly = 1) { spotCachePort.putGallery("100", DomainLocale.KO, gallery) }
    }

    @Test
    fun `갤러리 캐시 히트면 upstream을 호출하지 않는다`() {
        every { spotCachePort.getGallery("100", DomainLocale.KO) } returns emptyList()

        assertTrue(service.spotGallery("100", DomainLocale.KO).isEmpty())
        verify(exactly = 0) { tourInfoPort.findGallery(any()) }
    }

    // ── 지연 필드: 운영 정보 ─────────────────────────────────────────

    @Test
    fun `운영정보는 캐시 미스 시 조회 후 저장한다`() {
        every { spotCachePort.getIntro("100", DomainLocale.KO, SpotCategory.TOURIST_SPOT) } returns null
        every { tourInfoPort.findIntro(any(), SpotCategory.TOURIST_SPOT) } returns intro()

        val view = service.spotIntro("100", SpotCategory.TOURIST_SPOT, DomainLocale.KO)

        assertEquals("09:00~18:00", view?.businessHours)
        verify(exactly = 1) { spotCachePort.putIntro("100", DomainLocale.KO, SpotCategory.TOURIST_SPOT, any()) }
    }

    @Test
    fun `운영정보가 값 없음으로 캐시돼 있으면 upstream을 다시 부르지 않는다`() {
        // 히트 판정은 래퍼로 한다 — value가 null이라고 미스로 취급하면 매번 upstream을 두드리게 된다.
        every {
            spotCachePort.getIntro("100", DomainLocale.KO, SpotCategory.TOURIST_SPOT)
        } returns CachedIntro(null)

        assertNull(service.spotIntro("100", SpotCategory.TOURIST_SPOT, DomainLocale.KO))
        verify(exactly = 0) { tourInfoPort.findIntro(any(), any()) }
    }

    @Test
    fun `운영정보 category를 그대로 포트에 전달한다`() {
        every { spotCachePort.getIntro(any(), any(), any()) } returns null
        every { tourInfoPort.findIntro(any(), SpotCategory.RESTAURANT) } returns null

        service.spotIntro("100", SpotCategory.RESTAURANT, DomainLocale.KO)

        verify(exactly = 1) { tourInfoPort.findIntro(any(), SpotCategory.RESTAURANT) }
    }

    // ── 지연 필드: 이용안내 ──────────────────────────────────────────

    @Test
    fun `이용안내는 캐시 미스 시 조회 후 저장한다`() {
        val items = listOf(SpotInfoItem("입장료", "무료"))
        every { spotCachePort.getInfoItems("100", DomainLocale.KO, SpotCategory.TOURIST_SPOT) } returns null
        every { tourInfoPort.findInfoItems(any(), SpotCategory.TOURIST_SPOT) } returns items

        val view = service.spotInfoItems("100", SpotCategory.TOURIST_SPOT, DomainLocale.KO)

        assertEquals("입장료", view.single().name)
        verify(exactly = 1) {
            spotCachePort.putInfoItems("100", DomainLocale.KO, SpotCategory.TOURIST_SPOT, items)
        }
    }

    // ── 지연 필드: 좋아요 수 ─────────────────────────────────────────

    @Test
    fun `AC-14 - 좋아요 수는 캐시하지 않고 매번 집계한다`() {
        every { spotQueryPort.countLikes("100", DomainLocale.KO) } returns 3L

        assertEquals(3L, service.spotLikeCount("100", DomainLocale.KO))
        // 캐시 포트는 좋아요에 관여하지 않는다.
        verify(exactly = 0) { spotCachePort.getDetail(any(), any()) }
    }

    @Test
    fun `AC-12 - 반응이 없으면 좋아요 수는 0`() {
        every { spotQueryPort.countLikes(any(), any()) } returns 0L
        assertEquals(0L, service.spotLikeCount("100", DomainLocale.KO))
    }
}
