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
import org.around.ieuri.tourism.application.port.out.cache.CachedIntro
import org.around.ieuri.tourism.application.port.out.cache.SpotCachePort
import org.around.ieuri.tourism.application.port.out.client.SpotDetailQuery
import org.around.ieuri.tourism.application.port.out.client.TourInfoPort
import org.around.ieuri.tourism.application.port.out.persistence.SpotDetailStorePort
import org.around.ieuri.tourism.application.port.out.persistence.StoredIntro
import org.around.ieuri.tourism.application.port.out.queue.SpotDetailWriteQueuePort
import org.around.ieuri.tourism.application.port.out.query.NearbySpotSearch
import org.around.ieuri.tourism.application.port.out.query.SpotQueryPort
import org.around.ieuri.tourism.application.port.out.query.SpotSearchResult
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
    private val spotDetailStorePort = mockk<SpotDetailStorePort>()
    private val writeQueuePort = mockk<SpotDetailWriteQueuePort>(relaxed = true)
    private val policy = SpotQueryPolicy(
        maxRadiusMeters = 100_000,
        maxPageSize = 100,
    )
    private val service = SpotQueryService(
        tourInfoPort, spotQueryPort, spotCachePort, spotDetailStorePort, writeQueuePort, policy,
    )

    // 기본: 아무것도 반응하지 않음(빈 집합). 개별 테스트에서 override.
    init {
        every { spotQueryPort.findReactedContentIds(any(), any()) } returns emptySet()
        every { spotQueryPort.searchNearby(any()) } returns emptyList()
        every { spotQueryPort.countNearby(any()) } returns 0
        // 기본: 캐시 미스 + 저장 성공. 캐시 히트 검증은 개별 테스트에서 override.
        every { spotCachePort.getDetail(any(), any()) } returns null
        every { spotCachePort.putDetail(any(), any(), any()) } returns Unit
        every { spotCachePort.getGallery(any(), any()) } returns null
        every { spotCachePort.putGallery(any(), any(), any()) } returns Unit
        every { spotCachePort.getIntro(any(), any(), any()) } returns null
        every { spotCachePort.putIntro(any(), any(), any(), any()) } returns Unit
        every { spotCachePort.getInfoItems(any(), any(), any()) } returns null
        every { spotCachePort.putInfoItems(any(), any(), any(), any()) } returns Unit
        // 기본: DB도 미스. 3단 폴백의 단계별 검증은 개별 테스트에서 override.
        every { spotDetailStorePort.findDetail(any(), any()) } returns null
        every { spotDetailStorePort.findGallery(any(), any()) } returns null
        every { spotDetailStorePort.findIntro(any(), any(), any()) } returns null
        every { spotDetailStorePort.findInfoItems(any(), any(), any()) } returns null
    }

    /**
     * 읽기 DTO 픽스처. 거리는 **DB가 계산해 돌려준 값**이므로 여기서 직접 지정한다
     * (SPEC-0004 이후 application은 거리를 계산하지 않는다).
     */
    private fun result(id: String, distanceMeters: Double): SpotSearchResult = SpotSearchResult(
        contentId = id,
        title = "spot-$id",
        address = "서울 어딘가",
        thumbnailImageUrl = "https://img/$id.jpg",
        mapX = 126.9780,
        mapY = 37.5665,
        category = SpotCategory.TOURIST_SPOT,
        copyright = CopyrightType.UNSPECIFIED,
        distanceMeters = distanceMeters,
    )

    private fun image() = SpotImage(
        imageUrl = "https://img/g1.jpg",
        thumbnailUrl = null,
        caption = null,
        copyright = CopyrightType.UNSPECIFIED,
    )

    private fun query(page: Int = 1, size: Int = 100, category: SpotCategory? = null, radius: Int = 20000) =
        SpotsQuery(
            latitude = 37.5665, longitude = 126.9780,
            radiusMeters = radius, category = category, page = page, size = size,
        )

    @Test
    fun `DB가 돌려준 순서를 그대로 유지한다(AC-25)`() {
        // 정렬은 이제 DB(ORDER BY ST_Distance, id)가 담당한다. 서비스가 재정렬하면
        // DB의 타이브레이크가 무의미해지므로, 받은 순서를 보존하는 것이 올바른 동작이다.
        every { spotQueryPort.searchNearby(any()) } returns
            listOf(result("a", 100.0), result("b", 200.0), result("c", 300.0))
        every { spotQueryPort.countNearby(any()) } returns 3

        val result = service.spots(query(), DomainLocale.KO, memberId = null)

        assertEquals(listOf("a", "b", "c"), result.items.map { it.id })
    }

    @Test
    fun `page와 size를 limit·offset으로 변환한다`() {
        val captured = slot<NearbySpotSearch>()
        every { spotQueryPort.searchNearby(capture(captured)) } returns emptyList()

        service.spots(query(page = 3, size = 50), DomainLocale.KO, memberId = null)

        assertEquals(50, captured.captured.limit)
        assertEquals(100, captured.captured.offset) // (3-1) * 50
    }

    @Test
    fun `hasNext는 totalCount와 같은 조건에서 산출된다`() {
        // 둘이 다른 기준을 쓰면 hasNext=false인데 totalCount는 더 있다고 말하는 상태가 생긴다
        // (런타임 계약 위험 3번).
        every { spotQueryPort.searchNearby(any()) } returns listOf(result("a", 100.0))
        every { spotQueryPort.countNearby(any()) } returns 200

        val result = service.spots(query(page = 1, size = 100), DomainLocale.KO, memberId = null)

        assertTrue(result.hasNext)
        assertEquals(1, result.page)
        assertEquals(200, result.totalCount)
    }

    @Test
    fun `마지막 페이지면 hasNext가 false다(AC-29)`() {
        every { spotQueryPort.searchNearby(any()) } returns listOf(result("a", 100.0))
        every { spotQueryPort.countNearby(any()) } returns 100

        val result = service.spots(query(page = 1, size = 100), DomainLocale.KO, memberId = null)

        assertFalse(result.hasNext)
    }

    @Test
    fun `distanceMeters는 DB 계산 값을 그대로 노출한다(AC-26)`() {
        every { spotQueryPort.searchNearby(any()) } returns listOf(result("a", 877.5))
        every { spotQueryPort.countNearby(any()) } returns 1

        val d = service.spots(query(), DomainLocale.KO, memberId = null).items.single().distanceMeters

        assertEquals(877.5, d)
    }

    @Test
    fun `size가 상한(100)을 넘으면 클램프된다(AC-30)`() {
        val captured = slot<NearbySpotSearch>()
        every { spotQueryPort.searchNearby(capture(captured)) } returns emptyList()

        service.spots(query(size = 500), DomainLocale.KO, memberId = null)

        assertEquals(100, captured.captured.limit)
    }

    @Test
    fun `spots 조회는 TourAPI를 호출하지 않는다(AC-21)`() {
        // 이 스펙의 핵심 주장 — 목록 조회 경로에서 upstream 의존이 완전히 사라진다.
        every { spotQueryPort.searchNearby(any()) } returns listOf(result("a", 100.0))
        every { spotQueryPort.countNearby(any()) } returns 1

        service.spots(query(), DomainLocale.KO, memberId = null)

        verify(exactly = 0) { tourInfoPort.findNearby(any()) }
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
            service.spots(query(category = SpotCategory.TRAVEL_COURSE), DomainLocale.EN, memberId = null)
        }
    }

    @Test
    fun `카테고리 필터가 조회 조건으로 전달된다(AC-33)`() {
        val captured = slot<NearbySpotSearch>()
        every { spotQueryPort.searchNearby(capture(captured)) } returns emptyList()

        service.spots(query(category = SpotCategory.LODGING), DomainLocale.KO, memberId = null)

        assertEquals(SpotCategory.LODGING, captured.captured.category)
    }

    @Test
    fun `로그인 회원이 이미 반응한 관광지는 DB 조회 단계에서 제외된다`() {
        // 조회 후 걸러내면 페이지가 요청한 size보다 작아지므로 쿼리 조건으로 넘긴다.
        val captured = slot<NearbySpotSearch>()
        every { spotQueryPort.findReactedContentIds(7L, DomainLocale.KO) } returns setOf("b")
        every { spotQueryPort.searchNearby(capture(captured)) } returns emptyList()

        service.spots(query(), DomainLocale.KO, memberId = 7L)

        assertEquals(setOf("b"), captured.captured.excludedContentIds)
    }

    @Test
    fun `비로그인은 반응 조회 없이 전부 반환한다`() {
        val captured = slot<NearbySpotSearch>()
        every { spotQueryPort.searchNearby(capture(captured)) } returns
            listOf(result("a", 100.0), result("b", 200.0))
        every { spotQueryPort.countNearby(any()) } returns 2

        val result = service.spots(query(), DomainLocale.KO, memberId = null)

        assertEquals(listOf("a", "b"), result.items.map { it.id })
        assertTrue(captured.captured.excludedContentIds.isEmpty())
        verify(exactly = 0) { spotQueryPort.findReactedContentIds(any(), any()) }
    }

    // ---------- 3단 폴백: Valkey → DB → TourAPI (SPEC-0004 R-13) ----------

    @Test
    fun `Valkey 히트면 DB와 TourAPI를 모두 호출하지 않는다(AC-34)`() {
        every { spotCachePort.getDetail("100", DomainLocale.KO) } returns detail()

        service.spotDetail("100", DomainLocale.KO)

        verify(exactly = 0) { spotDetailStorePort.findDetail(any(), any()) }
        verify(exactly = 0) { tourInfoPort.findDetail(any()) }
    }

    @Test
    fun `Valkey 미스에 DB 히트면 TourAPI를 치지 않고 Valkey를 충전한다(AC-35)`() {
        every { spotDetailStorePort.findDetail("100", DomainLocale.KO) } returns detail()

        val result = service.spotDetail("100", DomainLocale.KO)

        assertEquals("경복궁", result.title)
        verify(exactly = 0) { tourInfoPort.findDetail(any()) }
        verify(exactly = 1) { spotCachePort.putDetail("100", DomainLocale.KO, any()) }
    }

    @Test
    fun `양쪽 미스면 TourAPI를 호출하고 적재 큐에 넣는다(AC-36·AC-39)`() {
        every { tourInfoPort.findDetail(any()) } returns detail()

        service.spotDetail("100", DomainLocale.KO)

        verify(exactly = 1) { tourInfoPort.findDetail(any()) }
        // 응답은 적재 완료를 기다리지 않는다 — 큐에 넣기만 한다.
        verify(exactly = 1) { writeQueuePort.enqueueDetail("100", DomainLocale.KO, any()) }
    }

    @Test
    fun `DB 히트 시에는 큐에 다시 넣지 않는다`() {
        // 이미 적재된 것을 또 넣으면 큐가 불필요하게 찬다.
        every { spotDetailStorePort.findDetail("100", DomainLocale.KO) } returns detail()

        service.spotDetail("100", DomainLocale.KO)

        verify(exactly = 0) { writeQueuePort.enqueueDetail(any(), any(), any()) }
    }

    @Test
    fun `갤러리도 같은 3단 구조를 갖는다`() {
        every { spotDetailStorePort.findGallery("100", DomainLocale.KO) } returns listOf(image())

        val result = service.spotGallery("100", DomainLocale.KO)

        assertEquals(1, result.size)
        verify(exactly = 0) { tourInfoPort.findGallery(any()) }
    }

    @Test
    fun `intro는 DB에 적재된 값이 null이어도 미스로 취급하지 않는다`() {
        // "적재됐으나 값 없음"을 미스로 오판하면 매 조회마다 upstream을 두드린다.
        every { spotDetailStorePort.findIntro("100", DomainLocale.KO, SpotCategory.TOURIST_SPOT) } returns
            StoredIntro(null)

        val result = service.spotIntro("100", SpotCategory.TOURIST_SPOT, DomainLocale.KO)

        assertNull(result)
        verify(exactly = 0) { tourInfoPort.findIntro(any(), any()) }
    }

    @Test
    fun `infoItems도 같은 3단 구조를 갖는다`() {
        every { spotDetailStorePort.findInfoItems("100", DomainLocale.KO, SpotCategory.TOURIST_SPOT) } returns
            listOf(SpotInfoItem("주차", "가능"))

        val result = service.spotInfoItems("100", SpotCategory.TOURIST_SPOT, DomainLocale.KO)

        assertEquals(1, result.size)
        verify(exactly = 0) { tourInfoPort.findInfoItems(any(), any()) }
    }

    @Test
    fun `likeCount는 3단 폴백 경로에 들어가지 않는다(AC-38)`() {
        every { spotQueryPort.countLikes("100", DomainLocale.KO) } returns 7L

        assertEquals(7L, service.spotLikeCount("100", DomainLocale.KO))

        verify(exactly = 0) { spotDetailStorePort.findDetail(any(), any()) }
        verify(exactly = 0) { writeQueuePort.enqueueDetail(any(), any(), any()) }
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
