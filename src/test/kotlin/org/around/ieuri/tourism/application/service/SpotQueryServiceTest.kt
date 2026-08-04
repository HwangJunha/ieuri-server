package org.around.ieuri.tourism.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.tourism.application.exception.CategoryNotAvailableForLocaleException
import org.around.ieuri.tourism.application.exception.InvalidCoordinatesException
import org.around.ieuri.tourism.application.exception.RadiusTooLargeException
import org.around.ieuri.tourism.application.exception.SpotDetailNotFoundException
import org.around.ieuri.tourism.application.exception.UpstreamUnavailableException
import org.around.ieuri.tourism.application.policy.SpotQueryPolicy
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.CursorPageInfo
import org.around.ieuri.tourism.application.port.out.cache.CachedDetail
import org.around.ieuri.tourism.application.port.out.cache.CachedIntro
import org.around.ieuri.tourism.application.port.out.cache.SpotCachePort
import org.around.ieuri.tourism.application.port.out.client.SpotDetailQuery
import org.around.ieuri.tourism.application.port.out.client.TourInfoPort
import org.around.ieuri.tourism.application.port.out.persistence.SpotDetailStorePort
import org.around.ieuri.tourism.application.port.out.persistence.StoredDetail
import org.around.ieuri.tourism.application.port.out.persistence.StoredIntro
import org.around.ieuri.tourism.application.event.SpotDetailAbsent
import org.around.ieuri.tourism.application.event.SpotDetailFetched
import org.around.ieuri.tourism.application.event.SpotGalleryFetched
import org.around.ieuri.tourism.application.exception.UpstreamUnavailableException
import org.around.ieuri.tourism.application.port.out.event.TourismEventPublisherPort
import org.around.ieuri.tourism.application.port.out.query.LikedSpotsSearch
import org.around.ieuri.tourism.application.port.out.query.NearbySpotSearch
import org.around.ieuri.tourism.application.port.out.query.SpotBasics
import org.around.ieuri.tourism.application.port.out.query.SpotIdentity
import org.around.ieuri.tourism.application.port.out.query.SpotQueryPort
import org.around.ieuri.tourism.application.port.out.query.SpotSearchResult
import org.around.ieuri.tourism.application.query.LikedSpotView
import org.around.ieuri.tourism.application.query.LikedSpotsQuery
import org.around.ieuri.tourism.application.query.SpotsQuery
import org.around.ieuri.tourism.domain.aggregate.SpotDetail
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.domain.vo.SpotImage
import org.around.ieuri.tourism.domain.vo.SpotInfoItem
import org.around.ieuri.tourism.domain.vo.SpotIntro
import java.time.Duration
import java.time.Instant
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
    private val eventPublisher = mockk<TourismEventPublisherPort>(relaxed = true)
    private val policy = SpotQueryPolicy(
        maxRadiusMeters = 100_000,
        maxPageSize = 100,
        maxLikedPageSize = 100,
        detailAbsenceTtl = Duration.ofDays(7),
    )
    private val service = SpotQueryService(
        tourInfoPort, spotQueryPort, spotCachePort, spotDetailStorePort, eventPublisher, policy,
    )

    // 기본: 아무것도 반응하지 않음(빈 집합). 개별 테스트에서 override.
    init {
        every { spotQueryPort.findReactedSpotIds(any(), any()) } returns emptySet()
        // spot.id → (contentId, language) 해석. 기본은 KO로 두고, 언어 검증을 다루는
        // 테스트에서만 override한다.
        every { spotQueryPort.findIdentity(any()) } answers {
            SpotIdentity(contentId = firstArg<Long>().toString(), language = DomainLocale.KO)
        }
        every { spotQueryPort.searchNearby(any()) } returns emptyList()
        every { spotQueryPort.countNearby(any()) } returns 0
        // 기본: 캐시 미스 + 저장 성공. 캐시 히트 검증은 개별 테스트에서 override.
        every { spotCachePort.getDetail(any()) } returns null
        every { spotCachePort.putDetail(any(), any()) } returns Unit
        every { spotCachePort.putDetailAbsence(any(), any()) } returns Unit
        // 부재 응답 구성용 spot 값(R-2). 부재를 다루지 않는 테스트에도 있어야 무해하다.
        every { spotQueryPort.findSpotBasics(any()) } answers { basics() }
        every { spotCachePort.getGallery(any()) } returns null
        every { spotCachePort.putGallery(any(), any()) } returns Unit
        every { spotCachePort.getIntro(any(), any()) } returns null
        every { spotCachePort.putIntro(any(), any(), any()) } returns Unit
        every { spotCachePort.getInfoItems(any(), any()) } returns null
        every { spotCachePort.putInfoItems(any(), any(), any()) } returns Unit
        // 기본: DB도 미스. 3단 폴백의 단계별 검증은 개별 테스트에서 override.
        every { spotDetailStorePort.findDetail(any()) } returns null
        every { spotDetailStorePort.findGallery(any()) } returns null
        every { spotDetailStorePort.findIntro(any(), any()) } returns null
        every { spotDetailStorePort.findInfoItems(any(), any()) } returns null
    }

    /**
     * 읽기 DTO 픽스처. 거리는 **DB가 계산해 돌려준 값**이므로 여기서 직접 지정한다
     * (SPEC-0004 이후 application은 거리를 계산하지 않는다).
     */
    private fun result(id: Long, distanceMeters: Double): SpotSearchResult = SpotSearchResult(
        spotId = id,
        title = "spot-$id",
        address = "서울 어딘가",
        thumbnailImageUrl = "https://img/$id.jpg",
        mapX = 126.9780,
        mapY = 37.5665,
        category = SpotCategory.TOURIST_SPOT,
        copyright = CopyrightType.UNSPECIFIED,
        distanceMeters = distanceMeters,
    )

    /** `spot` 행이 보유한 값 — 본문 부재 시 응답을 구성하는 재료(R-2). */
    private fun basics(address: String? = "서울 종로구") = SpotBasics(
        title = "경복궁",
        address = address,
        thumbnailImageUrl = "https://img/100.jpg",
        category = SpotCategory.TOURIST_SPOT,
        copyright = CopyrightType.TYPE1,
    )

    /** 적재된 본문(존재). */
    private fun stored(detail: SpotDetail = detail()) =
        StoredDetail(detail = detail, bodyAbsent = false, fetchedAt = Instant.now())

    /** 적재된 부재 행. [age]로 신선도 경과를 만든다. */
    private fun storedAbsence(age: Duration = Duration.ZERO) = StoredDetail(
        detail = SpotDetail(
            title = "경복궁",
            category = SpotCategory.TOURIST_SPOT,
            thumbnailImageUrl = "https://img/100.jpg",
            overview = null,
            address = "서울 종로구",
            homepage = null,
            tel = null,
            copyright = CopyrightType.TYPE1,
        ),
        bodyAbsent = true,
        fetchedAt = Instant.now().minus(age),
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
            listOf(result(1L, 100.0), result(2L, 200.0), result(3L, 300.0))
        every { spotQueryPort.countNearby(any()) } returns 3

        val result = service.spots(query(), DomainLocale.KO, memberId = null)

        assertEquals(listOf(1L, 2L, 3L), result.items.map { it.id })
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
        every { spotQueryPort.searchNearby(any()) } returns listOf(result(1L, 100.0))
        every { spotQueryPort.countNearby(any()) } returns 200

        val result = service.spots(query(page = 1, size = 100), DomainLocale.KO, memberId = null)

        assertTrue(result.hasNext)
        assertEquals(1, result.page)
        assertEquals(200, result.totalCount)
    }

    @Test
    fun `마지막 페이지면 hasNext가 false다(AC-29)`() {
        every { spotQueryPort.searchNearby(any()) } returns listOf(result(1L, 100.0))
        every { spotQueryPort.countNearby(any()) } returns 100

        val result = service.spots(query(page = 1, size = 100), DomainLocale.KO, memberId = null)

        assertFalse(result.hasNext)
    }

    @Test
    fun `distanceMeters는 DB 계산 값을 그대로 노출한다(AC-26)`() {
        every { spotQueryPort.searchNearby(any()) } returns listOf(result(1L, 877.5))
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
        every { spotQueryPort.searchNearby(any()) } returns listOf(result(1L, 100.0))
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
        every { spotQueryPort.findReactedSpotIds(7L, DomainLocale.KO) } returns setOf(2L)
        every { spotQueryPort.searchNearby(capture(captured)) } returns emptyList()

        service.spots(query(), DomainLocale.KO, memberId = 7L)

        assertEquals(setOf(2L), captured.captured.excludedSpotIds)
    }

    @Test
    fun `비로그인은 반응 조회 없이 전부 반환한다`() {
        val captured = slot<NearbySpotSearch>()
        every { spotQueryPort.searchNearby(capture(captured)) } returns
            listOf(result(1L, 100.0), result(2L, 200.0))
        every { spotQueryPort.countNearby(any()) } returns 2

        val result = service.spots(query(), DomainLocale.KO, memberId = null)

        assertEquals(listOf(1L, 2L), result.items.map { it.id })
        assertTrue(captured.captured.excludedSpotIds.isEmpty())
        verify(exactly = 0) { spotQueryPort.findReactedSpotIds(any(), any()) }
    }

    // ---------- 3단 폴백: Valkey → DB → TourAPI (SPEC-0004 R-13) ----------

    @Test
    fun `Valkey 히트면 DB와 TourAPI를 모두 호출하지 않는다(AC-34)`() {
        every { spotCachePort.getDetail(100L) } returns CachedDetail(detail(), bodyAbsent = false)

        service.spotDetail(100L, DomainLocale.KO)

        verify(exactly = 0) { spotDetailStorePort.findDetail(any()) }
        verify(exactly = 0) { tourInfoPort.findDetail(any()) }
    }

    @Test
    fun `Valkey 미스에 DB 히트면 TourAPI를 치지 않고 Valkey를 충전한다(AC-35)`() {
        every { spotDetailStorePort.findDetail(100L) } returns stored()

        val result = service.spotDetail(100L, DomainLocale.KO)

        assertEquals("경복궁", result.title)
        verify(exactly = 0) { tourInfoPort.findDetail(any()) }
        verify(exactly = 1) { spotCachePort.putDetail(100L, any()) }
    }

    @Test
    fun `양쪽 미스면 TourAPI를 호출하고 적재 큐에 넣는다(AC-36·AC-39)`() {
        every { tourInfoPort.findDetail(any()) } returns detail()

        service.spotDetail(100L, DomainLocale.KO)

        verify(exactly = 1) { tourInfoPort.findDetail(any()) }
        // 응답은 적재 완료를 기다리지 않는다 — 이벤트만 발행하고, 리스너가 @Async로 처리한다.
        verify(exactly = 1) { eventPublisher.publish(ofType(SpotDetailFetched::class)) }
    }

    @Test
    fun `DB 히트 시에는 이벤트를 발행하지 않는다`() {
        // 이미 적재된 것을 또 발행하면 큐가 불필요하게 찬다.
        every { spotDetailStorePort.findDetail(100L) } returns stored()

        service.spotDetail(100L, DomainLocale.KO)

        verify(exactly = 0) { eventPublisher.publish(any()) }
    }

    @Test
    fun `갤러리도 같은 3단 구조를 갖는다`() {
        every { spotDetailStorePort.findGallery(100L) } returns listOf(image())

        val result = service.spotGallery(100L, DomainLocale.KO)

        assertEquals(1, result.size)
        verify(exactly = 0) { tourInfoPort.findGallery(any()) }
    }

    @Test
    fun `intro는 DB에 적재된 값이 null이어도 미스로 취급하지 않는다`() {
        // "적재됐으나 값 없음"을 미스로 오판하면 매 조회마다 upstream을 두드린다.
        every { spotDetailStorePort.findIntro(100L, SpotCategory.TOURIST_SPOT) } returns
            StoredIntro(null)

        val result = service.spotIntro(100L, SpotCategory.TOURIST_SPOT, DomainLocale.KO)

        assertNull(result)
        verify(exactly = 0) { tourInfoPort.findIntro(any(), any()) }
    }

    @Test
    fun `infoItems도 같은 3단 구조를 갖는다`() {
        every { spotDetailStorePort.findInfoItems(100L, SpotCategory.TOURIST_SPOT) } returns
            listOf(SpotInfoItem("주차", "가능"))

        val result = service.spotInfoItems(100L, SpotCategory.TOURIST_SPOT, DomainLocale.KO)

        assertEquals(1, result.size)
        verify(exactly = 0) { tourInfoPort.findInfoItems(any(), any()) }
    }

    @Test
    fun `likeCount는 3단 폴백 경로에 들어가지 않는다(AC-38)`() {
        every { spotQueryPort.countLikes(100L) } returns 7L

        assertEquals(7L, service.spotLikeCount(100L))

        verify(exactly = 0) { spotDetailStorePort.findDetail(any()) }
        verify(exactly = 0) { eventPublisher.publish(any()) }
    }

    @Test
    fun `availableCategories는 언어별 유효 집합을 반환한다`() {
        assertTrue(SpotCategory.TRAVEL_COURSE in service.availableCategories(DomainLocale.KO))
        assertTrue(SpotCategory.TRANSPORT in service.availableCategories(DomainLocale.EN))
    }

    // ── spotDetail(SPEC-0003) ───────────────────────────────────────

    private fun detail(likeCount: Long = 0) = SpotDetail(
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

        val view = service.spotDetail(100L, DomainLocale.KO)

        assertEquals(100L, view.id)
        assertEquals("조선의 법궁", view.overview)
        assertEquals(CopyrightType.TYPE1.attributionText, view.attributionText)
    }

    @Test
    fun `본문만 요청하면 갤러리·운영정보·이용안내·좋아요를 조회하지 않는다`() {
        every { tourInfoPort.findDetail(any()) } returns detail()

        service.spotDetail(100L, DomainLocale.KO)

        // GraphQL 필드별 지연 조회의 핵심 — 안 물어본 건 upstream/DB를 건드리지 않는다.
        verify(exactly = 0) { tourInfoPort.findGallery(any()) }
        verify(exactly = 0) { tourInfoPort.findIntro(any(), any()) }
        verify(exactly = 0) { tourInfoPort.findInfoItems(any(), any()) }
        verify(exactly = 0) { spotQueryPort.countLikes(any()) }
    }

    @Test
    fun `AC-19 - 본문 캐시 히트면 upstream을 호출하지 않는다`() {
        every { spotCachePort.getDetail(100L) } returns CachedDetail(detail(), bodyAbsent = false)

        service.spotDetail(100L, DomainLocale.KO)

        verify(exactly = 0) { tourInfoPort.findDetail(any()) }
        verify(exactly = 0) { spotCachePort.putDetail(any(), any()) }
    }

    @Test
    fun `AC-18 - 본문 캐시 미스면 upstream 조회 후 저장한다`() {
        val fetched = detail()
        every { spotCachePort.getDetail(100L) } returns null
        every { tourInfoPort.findDetail(any()) } returns fetched

        service.spotDetail(100L, DomainLocale.KO)

        verify(exactly = 1) { tourInfoPort.findDetail(any()) }
        verify(exactly = 1) { spotCachePort.putDetail(100L, fetched) }
    }

    @Test
    fun `upstream이 실패하면 캐시에 저장하지 않는다(에러 비캐시)`() {
        every { spotCachePort.getDetail(any()) } returns null
        every { tourInfoPort.findDetail(any()) } throws SpotDetailNotFoundException()

        assertFailsWith<SpotDetailNotFoundException> { service.spotDetail(404L, DomainLocale.KO) }
        verify(exactly = 0) { spotCachePort.putDetail(any(), any()) }
    }

    @Test
    fun `spot_id를 contentId로 해석해 upstream에 전달한다`() {
        // V20 이후 외부 식별자(spot.id)와 upstream 식별자(contentId)가 분리됐다 —
        // 그대로 넘기는 것이 아니라 해석해서 넘긴다.
        every { spotQueryPort.findIdentity(777L) } returns
            SpotIdentity(contentId = "ta-999", language = DomainLocale.JA)
        val captured = slot<SpotDetailQuery>()
        every { tourInfoPort.findDetail(capture(captured)) } returns detail()

        service.spotDetail(777L, DomainLocale.JA)

        assertEquals("ta-999", captured.captured.contentId)
        assertEquals(DomainLocale.JA, captured.captured.locale)
    }

    @Test
    fun `요청 언어와 다른 언어의 spot_id는 거부한다`() {
        // 클라이언트가 언어를 바꾸면 목록을 다시 받아야 한다 — 이전 언어의 id로 상세를
        // 요청하는 것은 버그이며, 조용히 다른 언어를 돌려주면 그 버그가 드러나지 않는다.
        every { spotQueryPort.findIdentity(777L) } returns
            SpotIdentity(contentId = "ta-999", language = DomainLocale.EN)

        assertFailsWith<SpotDetailNotFoundException> { service.spotDetail(777L, DomainLocale.KO) }
        verify(exactly = 0) { tourInfoPort.findDetail(any()) }
    }

    @Test
    fun `캐시가 히트해도 언어가 다르면 거부한다`() {
        // **회귀 방지.** 언어 검증을 upstream 폴백 직전에 두면 캐시 히트에서 그대로 통과해,
        // 정작 가장 흔한 경로에서 교차 언어 콘텐츠가 나간다. spot_detail과 캐시 키 모두 언어를
        // 들지 않으므로(V20) 그 단계에서는 스스로 판별할 수 없다.
        every { spotQueryPort.findIdentity(777L) } returns
            SpotIdentity(contentId = "ta-999", language = DomainLocale.EN)
        every { spotCachePort.getDetail(777L) } returns CachedDetail(detail(), bodyAbsent = false)

        assertFailsWith<SpotDetailNotFoundException> { service.spotDetail(777L, DomainLocale.KO) }
    }

    @Test
    fun `지연 필드도 언어가 다르면 거부한다`() {
        // 갤러리·운영정보·이용안내가 각각 독립 진입점이라 전부 검증해야 한다 —
        // 하나라도 빠지면 그 필드만 교차 언어로 새어 나간다.
        every { spotQueryPort.findIdentity(777L) } returns
            SpotIdentity(contentId = "ta-999", language = DomainLocale.EN)
        every { spotCachePort.getGallery(777L) } returns emptyList()

        assertFailsWith<SpotDetailNotFoundException> { service.spotGallery(777L, DomainLocale.KO) }
        assertFailsWith<SpotDetailNotFoundException> {
            service.spotIntro(777L, SpotCategory.TOURIST_SPOT, DomainLocale.KO)
        }
        assertFailsWith<SpotDetailNotFoundException> {
            service.spotInfoItems(777L, SpotCategory.TOURIST_SPOT, DomainLocale.KO)
        }
    }

    @Test
    fun `존재하지 않는 spot_id는 거부한다`() {
        every { spotQueryPort.findIdentity(404L) } returns null

        assertFailsWith<SpotDetailNotFoundException> { service.spotDetail(404L, DomainLocale.KO) }
    }

    // ── 지연 필드: 갤러리 ────────────────────────────────────────────

    @Test
    fun `갤러리는 캐시 미스 시 조회 후 저장한다`() {
        val gallery = listOf(SpotImage("https://img/g1.jpg", null, "야경", CopyrightType.TYPE3))
        every { spotCachePort.getGallery(100L) } returns null
        every { tourInfoPort.findGallery(any()) } returns gallery

        val view = service.spotGallery(100L, DomainLocale.KO)

        assertEquals(1, view.size)
        assertEquals(CopyrightType.TYPE3.attributionText, view.single().attributionText)
        verify(exactly = 1) { spotCachePort.putGallery(100L, gallery) }
    }

    @Test
    fun `갤러리 캐시 히트면 upstream을 호출하지 않는다`() {
        every { spotCachePort.getGallery(100L) } returns emptyList()

        assertTrue(service.spotGallery(100L, DomainLocale.KO).isEmpty())
        verify(exactly = 0) { tourInfoPort.findGallery(any()) }
    }

    // ── 지연 필드: 운영 정보 ─────────────────────────────────────────

    @Test
    fun `운영정보는 캐시 미스 시 조회 후 저장한다`() {
        every { spotCachePort.getIntro(100L, SpotCategory.TOURIST_SPOT) } returns null
        every { tourInfoPort.findIntro(any(), SpotCategory.TOURIST_SPOT) } returns intro()

        val view = service.spotIntro(100L, SpotCategory.TOURIST_SPOT, DomainLocale.KO)

        assertEquals("09:00~18:00", view?.businessHours)
        verify(exactly = 1) { spotCachePort.putIntro(100L, SpotCategory.TOURIST_SPOT, any()) }
    }

    @Test
    fun `운영정보가 값 없음으로 캐시돼 있으면 upstream을 다시 부르지 않는다`() {
        // 히트 판정은 래퍼로 한다 — value가 null이라고 미스로 취급하면 매번 upstream을 두드리게 된다.
        every {
            spotCachePort.getIntro(100L, SpotCategory.TOURIST_SPOT)
        } returns CachedIntro(null)

        assertNull(service.spotIntro(100L, SpotCategory.TOURIST_SPOT, DomainLocale.KO))
        verify(exactly = 0) { tourInfoPort.findIntro(any(), any()) }
    }

    @Test
    fun `운영정보 category를 그대로 포트에 전달한다`() {
        every { spotCachePort.getIntro(any(), any()) } returns null
        every { tourInfoPort.findIntro(any(), SpotCategory.RESTAURANT) } returns null

        service.spotIntro(100L, SpotCategory.RESTAURANT, DomainLocale.KO)

        verify(exactly = 1) { tourInfoPort.findIntro(any(), SpotCategory.RESTAURANT) }
    }

    // ── 지연 필드: 이용안내 ──────────────────────────────────────────

    @Test
    fun `이용안내는 캐시 미스 시 조회 후 저장한다`() {
        val items = listOf(SpotInfoItem("입장료", "무료"))
        every { spotCachePort.getInfoItems(100L, SpotCategory.TOURIST_SPOT) } returns null
        every { tourInfoPort.findInfoItems(any(), SpotCategory.TOURIST_SPOT) } returns items

        val view = service.spotInfoItems(100L, SpotCategory.TOURIST_SPOT, DomainLocale.KO)

        assertEquals("입장료", view.single().name)
        verify(exactly = 1) {
            spotCachePort.putInfoItems(100L, SpotCategory.TOURIST_SPOT, items)
        }
    }

    // ── 지연 필드: 좋아요 수 ─────────────────────────────────────────

    @Test
    fun `AC-14 - 좋아요 수는 캐시하지 않고 매번 집계한다`() {
        every { spotQueryPort.countLikes(100L) } returns 3L

        assertEquals(3L, service.spotLikeCount(100L))
        // 캐시 포트는 좋아요에 관여하지 않는다.
        verify(exactly = 0) { spotCachePort.getDetail(any()) }
    }

    @Test
    fun `AC-12 - 반응이 없으면 좋아요 수는 0`() {
        every { spotQueryPort.countLikes(any()) } returns 0L
        assertEquals(0L, service.spotLikeCount(100L))
    }


    // ══════════════════════════════════════════════════════════════════
    // SPEC-0008 : 상세 본문 부재 (R-1 ~ R-4)
    //
    // 이 절의 핵심 주장은 하나다 — **부재와 실패는 다른 것이다.** 부재는 다시 물어도 결과가 같은
    // 사실이라 적재하고 성공으로 응답하고, 실패는 다음엔 될 수 있으므로 적재하지 않고 표면화한다.
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `AC-1 - 본문이 부재해도 예외 없이 SpotDetail을 반환한다`() {
        every { tourInfoPort.findDetail(any()) } returns null

        val view = service.spotDetail(100L, DomainLocale.KO)

        assertEquals(100L, view.id)
        assertEquals("경복궁", view.title)
    }

    @Test
    fun `AC-2 - 본문 부재는 SPOT_NOT_FOUND가 아니다`() {
        // 부재는 오류가 아니라 성공 경로다. 이 둘을 섞으면 클라이언트가 "spot이 없음"과
        // "상세만 없음"을 구분할 수 없고, 후자는 축약 화면으로 정상 렌더되어야 한다.
        every { tourInfoPort.findDetail(any()) } returns null

        service.spotDetail(100L, DomainLocale.KO) // 예외가 나면 실패
    }

    @Test
    fun `AC-3 - spot 자체가 없으면 SPOT_NOT_FOUND다`() {
        // 본문 부재와 id 미해석은 서로 다른 경로다.
        every { spotQueryPort.findIdentity(404L) } returns null

        assertFailsWith<SpotDetailNotFoundException> { service.spotDetail(404L, DomainLocale.KO) }
    }

    @Test
    fun `AC-4 - 포트가 예외를 던지면 부재로 적재하지 않고 실패로 표면화한다`() {
        // **이 스펙에서 가장 중요한 경계다.** 일시적 장애를 부재로 적재하면 신선도 기준이 지날
        // 때까지 데이터가 복구되지 않는다.
        every { tourInfoPort.findDetail(any()) } throws UpstreamUnavailableException("upstream down")

        assertFailsWith<UpstreamUnavailableException> { service.spotDetail(100L, DomainLocale.KO) }

        verify(exactly = 0) { eventPublisher.publish(ofType(SpotDetailAbsent::class)) }
        verify(exactly = 0) { spotCachePort.putDetailAbsence(any(), any()) }
    }

    @Test
    fun `AC-5·AC-6 - 부재 시 응답은 spot 행의 값으로 구성된다`() {
        every { tourInfoPort.findDetail(any()) } returns null

        val view = service.spotDetail(100L, DomainLocale.KO)

        assertEquals("경복궁", view.title)
        assertEquals(SpotCategory.TOURIST_SPOT, view.category)
        assertEquals("https://img/100.jpg", view.thumbnailImageUrl)
        assertEquals(CopyrightType.TYPE1, view.copyright)
        // attributionText는 copyright에서 도출되므로 부재 시에도 값이 있다.
        assertEquals(CopyrightType.TYPE1.attributionText, view.attributionText)
    }

    @Test
    fun `AC-7 - 부재 시 overview·homepage·tel은 null이다`() {
        every { tourInfoPort.findDetail(any()) } returns null

        val view = service.spotDetail(100L, DomainLocale.KO)

        assertNull(view.overview)
        assertNull(view.homepage)
        assertNull(view.tel)
    }

    @Test
    fun `AC-8 - spot의 address가 null이어도 부재 조회는 성공한다`() {
        every { spotQueryPort.findSpotBasics(any()) } returns basics(address = null)
        every { tourInfoPort.findDetail(any()) } returns null

        val view = service.spotDetail(100L, DomainLocale.KO)

        assertNull(view.address)
        assertEquals("경복궁", view.title)
    }

    @Test
    fun `AC-9 - 부재가 적재된 spot은 upstream을 다시 호출하지 않는다`() {
        // 부재 적재의 존재 이유 그 자체 — 없는 데이터를 매 조회마다 다시 묻지 않는다.
        every { spotDetailStorePort.findDetail(100L) } returns storedAbsence()

        val view = service.spotDetail(100L, DomainLocale.KO)

        assertNull(view.overview)
        verify(exactly = 0) { tourInfoPort.findDetail(any()) }
    }

    @Test
    fun `AC-10 - 한 번도 조회되지 않은 spot은 upstream을 호출한다`() {
        // 행 없음(미조회)과 행 있음+부재는 다른 상태다.
        every { spotDetailStorePort.findDetail(100L) } returns null
        every { tourInfoPort.findDetail(any()) } returns detail()

        service.spotDetail(100L, DomainLocale.KO)

        verify(exactly = 1) { tourInfoPort.findDetail(any()) }
    }

    @Test
    fun `AC-11 - 부재와 미조회는 서로 다른 상태로 구분된다`() {
        // 미조회 → upstream을 친다.
        every { spotDetailStorePort.findDetail(1L) } returns null
        every { tourInfoPort.findDetail(any()) } returns detail()
        service.spotDetail(1L, DomainLocale.KO)
        verify(exactly = 1) { tourInfoPort.findDetail(any()) }

        // 부재 적재됨 → 치지 않는다. 둘을 같은 null로 표현했다면 이 구분이 불가능하다.
        every { spotDetailStorePort.findDetail(2L) } returns storedAbsence()
        service.spotDetail(2L, DomainLocale.KO)
        verify(exactly = 1) { tourInfoPort.findDetail(any()) } // 늘지 않았다
    }

    @Test
    fun `AC-12 - 신선도 기준 이전이면 여전히 부재로 응답한다`() {
        every { spotDetailStorePort.findDetail(100L) } returns storedAbsence(age = Duration.ofDays(3))

        val view = service.spotDetail(100L, DomainLocale.KO)

        assertNull(view.overview)
        verify(exactly = 0) { tourInfoPort.findDetail(any()) }
    }

    @Test
    fun `AC-13 - 부재의 신선도가 지나면 upstream에 다시 묻는다`() {
        every { spotDetailStorePort.findDetail(100L) } returns storedAbsence(age = Duration.ofDays(8))
        every { tourInfoPort.findDetail(any()) } returns null

        service.spotDetail(100L, DomainLocale.KO)

        verify(exactly = 1) { tourInfoPort.findDetail(any()) }
    }

    @Test
    fun `AC-14 - 재조회 결과 본문이 생기면 본문 있는 상태로 전환된다`() {
        every { spotDetailStorePort.findDetail(100L) } returns storedAbsence(age = Duration.ofDays(8))
        every { tourInfoPort.findDetail(any()) } returns detail()

        val view = service.spotDetail(100L, DomainLocale.KO)

        assertEquals("조선의 법궁", view.overview)
        // 부재가 아니라 본문 적재 이벤트가 나가야 body_absent가 false로 되돌아간다.
        verify(exactly = 1) { eventPublisher.publish(ofType(SpotDetailFetched::class)) }
        verify(exactly = 0) { eventPublisher.publish(ofType(SpotDetailAbsent::class)) }
    }

    @Test
    fun `AC-15 - 재조회가 여전히 부재면 적재 시각이 갱신된다`() {
        // 갱신하지 않으면 신선도 경과가 누적된 채로 남아 매 조회마다 upstream을 치는 상태로
        // 퇴화한다 — 부재 적재가 무의미해지는 지점이다.
        every { spotDetailStorePort.findDetail(100L) } returns storedAbsence(age = Duration.ofDays(8))
        every { tourInfoPort.findDetail(any()) } returns null

        service.spotDetail(100L, DomainLocale.KO)

        // 부재 이벤트 재발행 = 어댑터의 upsert가 fetched_at을 now()로 갱신 → 창이 새로 시작된다.
        verify(exactly = 1) { eventPublisher.publish(ofType(SpotDetailAbsent::class)) }
    }

    @Test
    fun `AC-16 - 재조회가 실패로 끝나면 적재 시각을 갱신하지 않는다`() {
        // 실패는 부재의 재확인이 아니므로 신선도 창을 연장해서는 안 된다.
        every { spotDetailStorePort.findDetail(100L) } returns storedAbsence(age = Duration.ofDays(8))
        every { tourInfoPort.findDetail(any()) } throws UpstreamUnavailableException("boom")

        assertFailsWith<UpstreamUnavailableException> { service.spotDetail(100L, DomainLocale.KO) }

        verify(exactly = 0) { eventPublisher.publish(any()) }
    }

    @Test
    fun `부재를 확인하면 적재 이벤트를 발행한다(R-3)`() {
        every { tourInfoPort.findDetail(any()) } returns null

        service.spotDetail(100L, DomainLocale.KO)

        verify(exactly = 1) { eventPublisher.publish(ofType(SpotDetailAbsent::class)) }
        // 본문 적재로 잘못 나가면 body_absent가 서지 않아 다음 조회가 다시 upstream을 친다.
        verify(exactly = 0) { eventPublisher.publish(ofType(SpotDetailFetched::class)) }
    }

    @Test
    fun `캐시에 부재가 담겨 있으면 DB·upstream을 모두 건너뛴다`() {
        // 부재를 캐시 미스로 오판하면 본문 없는 spot이 TTL 내내 매 조회마다 DB·upstream을 탄다.
        every { spotCachePort.getDetail(100L) } returns
            CachedDetail(storedAbsence().detail, bodyAbsent = true)

        val view = service.spotDetail(100L, DomainLocale.KO)

        assertNull(view.overview)
        assertEquals("경복궁", view.title)
        verify(exactly = 0) { spotDetailStorePort.findDetail(any()) }
        verify(exactly = 0) { tourInfoPort.findDetail(any()) }
    }

    @Test
    fun `DB의 부재 행을 읽으면 캐시도 부재로 충전한다`() {
        // 부재를 일반 본문으로 캐시하면 캐시 히트 시 bodyAbsent가 false로 되살아난다.
        every { spotDetailStorePort.findDetail(100L) } returns storedAbsence()

        service.spotDetail(100L, DomainLocale.KO)

        verify(exactly = 1) { spotCachePort.putDetailAbsence(100L, any()) }
        verify(exactly = 0) { spotCachePort.putDetail(any(), any()) }
    }

    // ══════════════════════════════════════════════════════════════════
    // SPEC-0008 : 좋아요 목록 (R-15 ~ R-17)
    // ══════════════════════════════════════════════════════════════════

    private fun likedSpot(id: Long, likedAt: Instant = Instant.parse("2026-08-01T00:00:00Z")) =
        LikedSpotView(
            id = id,
            title = "spot-$id",
            thumbnailImageUrl = "https://img/$id.jpg",
            address = null,
            category = SpotCategory.TOURIST_SPOT,
            copyright = CopyrightType.TYPE1,
            attributionText = CopyrightType.TYPE1.attributionText,
            likedAt = likedAt,
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
        // 본인 것만·요청 언어만이라는 두 제약이 쿼리 단계에서 걸려야 한다. 조회 후 걸러내면
        // 페이지가 요청한 크기보다 작아진다.
        val captured = slot<LikedSpotsSearch>()
        every { spotQueryPort.findLikedSpots(capture(captured)) } returns likedPage()

        service.likedSpots(LikedSpotsQuery(category = null, first = 20, after = null), DomainLocale.EN, memberId = 7L)

        assertEquals(7L, captured.captured.memberId)
        assertEquals(DomainLocale.EN, captured.captured.language)
    }

    @Test
    fun `AC-45 - 포트가 돌려준 목록을 그대로 반환한다`() {
        // LIKE 필터·정렬은 포트(쿼리)의 책임이다 — 서비스가 재정렬하면 DB의 타이브레이크가
        // 무의미해진다(spots와 같은 규율).
        every { spotQueryPort.findLikedSpots(any()) } returns likedPage(likedSpot(1L), likedSpot(2L))

        val page = service.likedSpots(LikedSpotsQuery(null, 20, null), DomainLocale.KO, memberId = 7L)

        assertEquals(listOf(1L, 2L), page.edges.map { it.node.id })
    }

    @Test
    fun `AC-52 - first가 상한을 초과하면 클램프된다`() {
        val captured = slot<LikedSpotsSearch>()
        every { spotQueryPort.findLikedSpots(capture(captured)) } returns likedPage()

        service.likedSpots(LikedSpotsQuery(category = null, first = 500, after = null), DomainLocale.KO, memberId = 7L)

        assertEquals(100, captured.captured.first)
    }

    @Test
    fun `first가 0 이하이면 최소 1로 보정된다`() {
        val captured = slot<LikedSpotsSearch>()
        every { spotQueryPort.findLikedSpots(capture(captured)) } returns likedPage()

        service.likedSpots(LikedSpotsQuery(category = null, first = 0, after = null), DomainLocale.KO, memberId = 7L)

        assertEquals(1, captured.captured.first)
    }

    @Test
    fun `AC-51 - after 커서가 조회 조건으로 전달된다`() {
        val captured = slot<LikedSpotsSearch>()
        every { spotQueryPort.findLikedSpots(capture(captured)) } returns likedPage()

        service.likedSpots(LikedSpotsQuery(null, 20, after = "cursor-abc"), DomainLocale.KO, memberId = 7L)

        assertEquals("cursor-abc", captured.captured.after)
    }

    @Test
    fun `카테고리 필터가 조회 조건으로 전달된다`() {
        val captured = slot<LikedSpotsSearch>()
        every { spotQueryPort.findLikedSpots(capture(captured)) } returns likedPage()

        service.likedSpots(
            LikedSpotsQuery(category = SpotCategory.RESTAURANT, first = 20, after = null),
            DomainLocale.KO,
            memberId = 7L,
        )

        assertEquals(SpotCategory.RESTAURANT, captured.captured.category)
    }

    @Test
    fun `카테고리 미지정이면 전체를 조회한다`() {
        val captured = slot<LikedSpotsSearch>()
        every { spotQueryPort.findLikedSpots(capture(captured)) } returns likedPage()

        service.likedSpots(LikedSpotsQuery(null, 20, null), DomainLocale.KO, memberId = 7L)

        assertNull(captured.captured.category)
    }

    @Test
    fun `현재 언어에 없는 카테고리는 거부한다(spots와 동일)`() {
        // 두 목록이 같은 카테고리 축을 쓰므로 판정도 같아야 한다 — 한쪽만 통과하면
        // 클라이언트가 카테고리 칩을 어느 화면에서 보여줄지 판단할 근거가 갈린다.
        assertFailsWith<CategoryNotAvailableForLocaleException> {
            service.likedSpots(
                LikedSpotsQuery(category = SpotCategory.TRAVEL_COURSE, first = 20, after = null),
                DomainLocale.EN,
                memberId = 7L,
            )
        }

        verify(exactly = 0) { spotQueryPort.findLikedSpots(any()) }
    }

    @Test
    fun `좋아요 목록은 상세 조회 경로를 건드리지 않는다`() {
        // 목록은 spot 행만 본다 — 상세가 부재한 spot도 정상적으로 나타나야 하므로
        // spot_detail·캐시·upstream 어느 것도 타지 않는다(AC-55의 전제).
        every { spotQueryPort.findLikedSpots(any()) } returns likedPage(likedSpot(1L))

        service.likedSpots(LikedSpotsQuery(null, 20, null), DomainLocale.KO, memberId = 7L)

        verify(exactly = 0) { spotDetailStorePort.findDetail(any()) }
        verify(exactly = 0) { tourInfoPort.findDetail(any()) }
        verify(exactly = 0) { spotCachePort.getDetail(any()) }
    }
}
