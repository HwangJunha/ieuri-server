package org.around.ieuri.tourism.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.common.context.AppKey
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.CursorPageInfo
import org.around.ieuri.tourism.application.event.SpotDetailAbsent
import org.around.ieuri.tourism.application.event.SpotDetailFetched
import org.around.ieuri.tourism.application.event.SpotGalleryFetched
import org.around.ieuri.tourism.application.exception.EmptyQueryException
import org.around.ieuri.tourism.application.exception.InvalidCoordinatesException
import org.around.ieuri.tourism.application.exception.QueryTooLongException
import org.around.ieuri.tourism.application.exception.RadiusTooLargeException
import org.around.ieuri.tourism.application.exception.SpotDetailNotFoundException
import org.around.ieuri.tourism.application.exception.SpotNotFoundInLocaleQueryException
import org.around.ieuri.tourism.application.exception.UnknownCategoryException
import org.around.ieuri.tourism.application.exception.UpstreamUnavailableException
import org.around.ieuri.tourism.application.policy.SpotQueryPolicy
import org.around.ieuri.tourism.application.port.out.cache.CachedDetail
import org.around.ieuri.tourism.application.port.out.cache.CachedIntro
import org.around.ieuri.tourism.application.port.out.cache.SpotCachePort
import org.around.ieuri.tourism.application.port.out.client.SpotDetailQuery
import org.around.ieuri.tourism.application.port.out.client.TourInfoPort
import org.around.ieuri.tourism.application.port.out.event.TourismEventPublisherPort
import org.around.ieuri.tourism.application.port.out.persistence.SpotDetailStorePort
import org.around.ieuri.tourism.application.port.out.persistence.StoredDetail
import org.around.ieuri.tourism.application.port.out.persistence.StoredIntro
import org.around.ieuri.tourism.application.port.out.query.AppCategoryQueryPort
import org.around.ieuri.tourism.application.port.out.query.LikedSpotSearchPort
import org.around.ieuri.tourism.application.port.out.query.LikedSpotsSearch
import org.around.ieuri.tourism.application.port.out.query.NearbySpotSearch
import org.around.ieuri.tourism.application.port.out.query.SpotBasics
import org.around.ieuri.tourism.application.port.out.query.SpotIdentity
import org.around.ieuri.tourism.application.port.out.query.SpotQueryPort
import org.around.ieuri.tourism.application.port.out.query.SpotSearchResult
import org.around.ieuri.tourism.application.port.out.query.SpotSpatialQueryPort
import org.around.ieuri.tourism.application.query.AppCategoryView
import org.around.ieuri.tourism.application.query.CategoryDisplay
import org.around.ieuri.tourism.application.query.LikedSpotView
import org.around.ieuri.tourism.application.query.LikedSpotsQuery
import org.around.ieuri.tourism.application.query.SpotsQuery
import org.around.ieuri.tourism.domain.aggregate.SpotDetail
import org.around.ieuri.tourism.domain.vo.CategorySelection
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.ReactionType
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.domain.vo.SpotImage
import org.around.ieuri.tourism.domain.vo.SpotInfoItem
import org.around.ieuri.tourism.domain.vo.SpotIntro
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** SpotQueryService — DTO→Domain 변환·거리계산·정렬(AC-1/2), 오프셋 페이지, 검증(AC-6/9), 가용성(AC-8). */
class SpotQueryServiceTest {

    private val tourInfoPort = mockk<TourInfoPort>()
    private val spotQueryPort = mockk<SpotQueryPort>()
    private val spotSpatialQueryPort = mockk<SpotSpatialQueryPort>()
    private val likedSpotSearchPort = mockk<LikedSpotSearchPort>()
    private val spotCachePort = mockk<SpotCachePort>()
    private val spotDetailStorePort = mockk<SpotDetailStorePort>()
    private val eventPublisher = mockk<TourismEventPublisherPort>(relaxed = true)
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
    private val appCategoryQueryPort = mockk<AppCategoryQueryPort>()
    private val service = SpotQueryService(
        tourInfoPort, spotQueryPort, spotSpatialQueryPort, likedSpotSearchPort, spotCachePort,
        spotDetailStorePort, eventPublisher, appCategoryQueryPort, policy,
    )

    // 기본: 아무것도 반응하지 않음(빈 집합). 개별 테스트에서 override.
    init {
        every { spotQueryPort.findReactedSpotIds(any(), any()) } returns emptySet()
        // 기본: 정의된 카테고리로 둔다.
        every { appCategoryQueryPort.exists(any()) } returns true
        // 기본: 어느 언어로 물어도 원본이 있다. spotId는 카탈로그 id와 다른 값을 준다.
        every { spotQueryPort.findIdentity(any(), any()) } answers {
            val catalogId = firstArg<Long>()
            SpotIdentity(
                spotId = catalogId + SPOT_ID_OFFSET,
                contentId = catalogId.toString(),
                mapX = CATALOG_MAP_X,
                mapY = CATALOG_MAP_Y,
            )
        }
        // 기본: 카탈로그 자체가 없다.
        every { spotQueryPort.existsCatalog(any()) } returns false
        every { spotSpatialQueryPort.searchNearby(any()) } returns emptyList()
        every { spotSpatialQueryPort.countNearby(any()) } returns 0
        // 기본: 캐시 미스 + 저장 성공. 캐시 히트 검증은 개별 테스트에서 override.
        every { spotCachePort.getDetail(any()) } returns null
        every { spotCachePort.putDetail(any(), any()) } returns Unit
        every { spotCachePort.putDetailAbsence(any(), any()) } returns Unit
        // 부재 응답 구성용 spot 값(R-2).
        every { spotQueryPort.findSpotBasics(any(), any()) } answers { basics() }
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

    /** 읽기 DTO 픽스처. 거리는 DB가 계산한 값으로 직접 지정한다. */
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
        longitude = 127.0,
        latitude = 37.5,
    )

    /** 적재된 본문(존재). */
    private fun stored(detail: SpotDetail = detail()) = StoredDetail(detail = detail, bodyAbsent = false, fetchedAt = Instant.now())

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
    fun `spots 조회는 TourAPI를 호출하지 않는다(AC-21)`() {
        every { spotSpatialQueryPort.searchNearby(any()) } returns listOf(result(1L, 100.0))
        every { spotSpatialQueryPort.countNearby(any()) } returns 1

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

    // ---------- 3단 폴백: Valkey → DB → TourAPI (SPEC-0004 R-13) ----------

    @Test
    fun `Valkey 히트면 DB와 TourAPI를 모두 호출하지 않는다(AC-34)`() {
        // 캐시 키는 카탈로그 id(100)가 아니라 원본 spot.id다.
        every { spotCachePort.getDetail(spotIdOf(100L)) } returns CachedDetail(detail(), bodyAbsent = false)

        service.spotDetail(100L, DomainLocale.KO)

        verify(exactly = 0) { spotDetailStorePort.findDetail(any()) }
        verify(exactly = 0) { tourInfoPort.findDetail(any()) }
    }

    @Test
    fun `Valkey 미스에 DB 히트면 TourAPI를 치지 않고 Valkey를 충전한다(AC-35)`() {
        val spotId = spotIdOf(100L)
        every { spotDetailStorePort.findDetail(spotId) } returns stored()

        val result = service.spotDetail(100L, DomainLocale.KO)

        assertEquals("경복궁", result.title)
        verify(exactly = 0) { tourInfoPort.findDetail(any()) }
        verify(exactly = 1) { spotCachePort.putDetail(spotId, any()) }
    }

    @Test
    fun `양쪽 미스면 TourAPI를 호출하고 적재 큐에 넣는다(AC-36·AC-39)`() {
        every { tourInfoPort.findDetail(any()) } returns detail()

        service.spotDetail(100L, DomainLocale.KO)

        verify(exactly = 1) { tourInfoPort.findDetail(any()) }
        verify(exactly = 1) { eventPublisher.publish(ofType(SpotDetailFetched::class)) }
    }

    @Test
    fun `DB 히트 시에는 이벤트를 발행하지 않는다`() {
        every { spotDetailStorePort.findDetail(spotIdOf(100L)) } returns stored()

        service.spotDetail(100L, DomainLocale.KO)

        verify(exactly = 0) { eventPublisher.publish(any()) }
    }

    @Test
    fun `갤러리도 같은 3단 구조를 갖는다`() {
        every { spotDetailStorePort.findGallery(spotIdOf(100L)) } returns listOf(image())

        val result = service.spotGallery(100L, DomainLocale.KO)

        assertEquals(1, result.size)
        verify(exactly = 0) { tourInfoPort.findGallery(any()) }
    }

    @Test
    fun `intro는 DB에 적재된 값이 null이어도 미스로 취급하지 않는다`() {
        every { spotDetailStorePort.findIntro(spotIdOf(100L), SpotCategory.TOURIST_SPOT) } returns
            StoredIntro(null)

        val result = service.spotIntro(100L, SpotCategory.TOURIST_SPOT, DomainLocale.KO)

        assertNull(result)
        verify(exactly = 0) { tourInfoPort.findIntro(any(), any()) }
    }

    @Test
    fun `infoItems도 같은 3단 구조를 갖는다`() {
        every { spotDetailStorePort.findInfoItems(spotIdOf(100L), SpotCategory.TOURIST_SPOT) } returns
            listOf(SpotInfoItem("주차", "가능"))

        val result = service.spotInfoItems(100L, SpotCategory.TOURIST_SPOT, DomainLocale.KO)

        assertEquals(1, result.size)
        verify(exactly = 0) { tourInfoPort.findInfoItems(any(), any()) }
    }

    @Test
    fun `likeCount는 3단 폴백 경로에 들어가지 않는다(AC-38)`() {
        every { spotQueryPort.countLikes(100L, DomainLocale.KO) } returns 7L

        assertEquals(7L, service.spotLikeCount(100L, DomainLocale.KO))

        verify(exactly = 0) { spotDetailStorePort.findDetail(any()) }
        verify(exactly = 0) { eventPublisher.publish(any()) }
    }

    /** 포트가 준 목록을 가공하지 않는다. 순서는 계약이 아니므로 집합으로 본다. */
    @Test
    fun `availableCategories는 앱과 언어로 구성을 조회해 그대로 돌려준다`() {
        val appKey = AppKey("demo-app")
        every { appCategoryQueryPort.findCategories(appKey, DomainLocale.KO) } returns listOf(
            AppCategoryView(SpotCategory("RESTAURANT"), "음식점", "https://cdn/restaurant.png"),
            AppCategoryView(SpotCategory("TRAVEL_COURSE"), "여행코스", null),
        )

        val result = service.availableCategories(appKey, DomainLocale.KO)

        assertEquals(setOf(SpotCategory("RESTAURANT"), SpotCategory("TRAVEL_COURSE")), result.map { it.category }.toSet())
        assertEquals(mapOf("RESTAURANT" to "음식점", "TRAVEL_COURSE" to "여행코스"), result.associate { it.category.code to it.label })
        assertEquals(
            mapOf("RESTAURANT" to "https://cdn/restaurant.png", "TRAVEL_COURSE" to null),
            result.associate { it.category.code to it.iconUrl },
        )
        verify(exactly = 1) { appCategoryQueryPort.findCategories(appKey, DomainLocale.KO) }
    }

    /** 다른 도메인이 쓰는 문자열 경로. */
    @Test
    fun `availableCategoryCodes는 문자열 축으로 같은 구성을 돌려준다`() {
        val appKey = AppKey("k-trip-spot")
        every { appCategoryQueryPort.findCategories(appKey, DomainLocale.JA) } returns
            listOf(AppCategoryView(SpotCategory("TRANSPORT"), "交通", "https://cdn/transport.png"))

        val result = service.availableCategoryCodes(appKey, "JA")

        assertEquals(listOf("TRANSPORT"), result.map { it.category })
        assertEquals(listOf("交通"), result.map { it.label })
        assertEquals(listOf("https://cdn/transport.png"), result.map { it.iconUrl })
    }

    /** 정의가 없는 코드는 코드 자체를 라벨로 채운다. */
    @Test
    fun `categoryDisplays는 정의가 없는 코드를 폴백으로 채운다`() {
        every { appCategoryQueryPort.findDisplays(setOf("RESTAURANT", "BRAND_NEW"), DomainLocale.KO) } returns
            mapOf("RESTAURANT" to CategoryDisplay(SpotCategory("RESTAURANT"), "음식점", "https://cdn/restaurant.png"))

        val result = service.categoryDisplays(setOf("RESTAURANT", "BRAND_NEW"), DomainLocale.KO)

        assertEquals(setOf("RESTAURANT", "BRAND_NEW"), result.keys)
        assertEquals("음식점", result["RESTAURANT"]?.label)
        assertEquals("https://cdn/restaurant.png", result["RESTAURANT"]?.iconUrl)
        assertEquals("BRAND_NEW", result["BRAND_NEW"]?.label)
        assertNull(result["BRAND_NEW"]?.iconUrl)
    }

    /** intro·infoItems는 정의된 카테고리만 받는다(SPEC-0020). */
    @Test
    fun `정의되지 않은 카테고리로 운영정보를 물으면 거절하고 아무것도 적재하지 않는다`() {
        every { appCategoryQueryPort.exists("NOT_A_CATEGORY") } returns false

        assertFailsWith<UnknownCategoryException> {
            service.spotIntro(1L, SpotCategory("NOT_A_CATEGORY"), DomainLocale.KO)
        }

        verify(exactly = 0) { spotCachePort.putIntro(any(), any(), any()) }
        verify(exactly = 0) { eventPublisher.publish(any()) }
    }

    @Test
    fun `정의되지 않은 카테고리로 이용안내를 물으면 거절한다`() {
        every { appCategoryQueryPort.exists("NOT_A_CATEGORY") } returns false

        assertFailsWith<UnknownCategoryException> {
            service.spotInfoItems(1L, SpotCategory("NOT_A_CATEGORY"), DomainLocale.KO)
        }

        verify(exactly = 0) { spotCachePort.putInfoItems(any(), any(), any()) }
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
        businessHours = "09:00~18:00",
        closedDays = "화요일",
        parking = "가능",
        inquiryPhone = null,
        fee = null,
        petAllowed = null,
        creditCardAccepted = null,
    )

    @Test
    fun `spotDetail은 본문만 반환한다(AC-1)`() {
        every { tourInfoPort.findDetail(any()) } returns detail()

        val view = service.spotDetail(100L, DomainLocale.KO)

        // 뷰의 id는 내부 spot.id가 아니라 클라이언트가 보낸 카탈로그 id다.
        assertEquals(100L, view.id)
        assertEquals("조선의 법궁", view.overview)
        assertEquals(CopyrightType.TYPE1.attributionText, view.attributionText)
    }

    @Test
    fun `본문만 요청하면 갤러리·운영정보·이용안내·좋아요를 조회하지 않는다`() {
        every { tourInfoPort.findDetail(any()) } returns detail()

        service.spotDetail(100L, DomainLocale.KO)

        verify(exactly = 0) { tourInfoPort.findGallery(any()) }
        verify(exactly = 0) { tourInfoPort.findIntro(any(), any()) }
        verify(exactly = 0) { tourInfoPort.findInfoItems(any(), any()) }
        verify(exactly = 0) { spotQueryPort.countLikes(any(), any()) }
    }

    @Test
    fun `AC-19 - 본문 캐시 히트면 upstream을 호출하지 않는다`() {
        every { spotCachePort.getDetail(spotIdOf(100L)) } returns CachedDetail(detail(), bodyAbsent = false)

        service.spotDetail(100L, DomainLocale.KO)

        verify(exactly = 0) { tourInfoPort.findDetail(any()) }
        verify(exactly = 0) { spotCachePort.putDetail(any(), any()) }
    }

    @Test
    fun `AC-18 - 본문 캐시 미스면 upstream 조회 후 저장한다`() {
        val fetched = detail()
        every { spotCachePort.getDetail(spotIdOf(100L)) } returns null
        every { tourInfoPort.findDetail(any()) } returns fetched

        service.spotDetail(100L, DomainLocale.KO)

        verify(exactly = 1) { tourInfoPort.findDetail(any()) }
        verify(exactly = 1) { spotCachePort.putDetail(spotIdOf(100L), fetched) }
    }

    @Test
    fun `upstream이 실패하면 캐시에 저장하지 않는다(에러 비캐시)`() {
        every { spotCachePort.getDetail(any()) } returns null
        every { tourInfoPort.findDetail(any()) } throws SpotDetailNotFoundException()

        assertFailsWith<SpotDetailNotFoundException> { service.spotDetail(404L, DomainLocale.KO) }
        verify(exactly = 0) { spotCachePort.putDetail(any(), any()) }
    }

    // ── 상세 좌표(SPEC-0027 R1): 네 경로 모두 좌표는 카탈로그에서 온다 ──────────

    @Test
    fun `R1-1 - upstream 경로의 상세에 카탈로그 좌표가 실린다`() {
        every { tourInfoPort.findDetail(any()) } returns detail()

        val view = service.spotDetail(100L, DomainLocale.KO)

        assertEquals(CATALOG_MAP_X, view.mapX)
        assertEquals(CATALOG_MAP_Y, view.mapY)
    }

    @Test
    fun `R1-2 - 본문이 캐시에서 와도 좌표가 실린다`() {
        every { spotCachePort.getDetail(spotIdOf(100L)) } returns CachedDetail(detail(), bodyAbsent = false)

        val view = service.spotDetail(100L, DomainLocale.KO)

        verify(exactly = 0) { tourInfoPort.findDetail(any()) }
        assertEquals(CATALOG_MAP_X, view.mapX)
        assertEquals(CATALOG_MAP_Y, view.mapY)
    }

    @Test
    fun `R1-2 - 본문이 DB에서 와도 좌표가 실린다`() {
        every { spotCachePort.getDetail(any()) } returns null
        every { spotDetailStorePort.findDetail(spotIdOf(100L)) } returns stored()

        val view = service.spotDetail(100L, DomainLocale.KO)

        verify(exactly = 0) { tourInfoPort.findDetail(any()) }
        assertEquals(CATALOG_MAP_X, view.mapX)
        assertEquals(CATALOG_MAP_Y, view.mapY)
    }

    @Test
    fun `R1-3 - 본문 부재 폴백에서도 좌표는 카탈로그에서 온다`() {
        // basics()는 일부러 다른 좌표를 갖고 있다.
        every { tourInfoPort.findDetail(any()) } returns null

        val view = service.spotDetail(100L, DomainLocale.KO)

        assertEquals(CATALOG_MAP_X, view.mapX, "폴백 좌표도 카탈로그(identity)에서 와야 한다")
        assertEquals(CATALOG_MAP_Y, view.mapY, "폴백 좌표도 카탈로그(identity)에서 와야 한다")
    }

    @Test
    fun `R1-1 - 경도와 위도가 뒤바뀌지 않는다`() {
        every { spotQueryPort.findIdentity(100L, DomainLocale.KO) } returns SpotIdentity(
            spotId = spotIdOf(100L),
            contentId = "100",
            mapX = 129.0756,
            mapY = 35.1796,
        )
        every { tourInfoPort.findDetail(any()) } returns detail()

        val view = service.spotDetail(100L, DomainLocale.KO)

        assertEquals(129.0756, view.mapX, "mapX는 경도다")
        assertEquals(35.1796, view.mapY, "mapY는 위도다")
    }

    @Test
    fun `catalog_id를 contentId로 해석해 upstream에 전달한다`() {
        every { spotQueryPort.findIdentity(777L, DomainLocale.JA) } returns
            SpotIdentity(
                spotId = 777L + SPOT_ID_OFFSET,
                contentId = "ta-999",
                mapX = CATALOG_MAP_X,
                mapY = CATALOG_MAP_Y,
            )
        val captured = slot<SpotDetailQuery>()
        every { tourInfoPort.findDetail(capture(captured)) } returns detail()

        service.spotDetail(777L, DomainLocale.JA)

        assertEquals("ta-999", captured.captured.contentId)
        assertEquals(DomainLocale.JA, captured.captured.locale)
    }

    @Test
    fun `원본이 하나도 없는 카탈로그는 거부한다`() {
        every { spotQueryPort.findIdentity(777L, DomainLocale.KO) } returns null

        assertFailsWith<SpotDetailNotFoundException> { service.spotDetail(777L, DomainLocale.KO) }
        verify(exactly = 0) { tourInfoPort.findDetail(any()) }
    }

    @Test
    fun `캐시가 히트해도 그 언어의 원본이 없으면 거부한다`() {
        every { spotQueryPort.findIdentity(777L, DomainLocale.KO) } returns null
        // 카탈로그는 있고 이 언어에만 원본이 없다(AC-12.3).
        every { spotQueryPort.existsCatalog(777L) } returns true
        every { spotCachePort.getDetail(any()) } returns CachedDetail(detail(), bodyAbsent = false)

        assertFailsWith<SpotNotFoundInLocaleQueryException> { service.spotDetail(777L, DomainLocale.KO) }
    }

    @Test
    fun `지연 필드도 그 언어의 원본이 없으면 거부한다`() {
        every { spotQueryPort.findIdentity(777L, DomainLocale.KO) } returns null
        every { spotQueryPort.existsCatalog(777L) } returns true
        every { spotCachePort.getGallery(any()) } returns emptyList()

        assertFailsWith<SpotNotFoundInLocaleQueryException> { service.spotGallery(777L, DomainLocale.KO) }
        assertFailsWith<SpotNotFoundInLocaleQueryException> {
            service.spotIntro(777L, SpotCategory.TOURIST_SPOT, DomainLocale.KO)
        }
        assertFailsWith<SpotNotFoundInLocaleQueryException> {
            service.spotInfoItems(777L, SpotCategory.TOURIST_SPOT, DomainLocale.KO)
        }
    }

    @Test
    fun `존재하지 않는 catalog_id는 거부한다`() {
        every { spotQueryPort.findIdentity(404L, DomainLocale.KO) } returns null

        assertFailsWith<SpotDetailNotFoundException> { service.spotDetail(404L, DomainLocale.KO) }
    }

    // ── 지연 필드: 갤러리 ────────────────────────────────────────────

    @Test
    fun `갤러리는 캐시 미스 시 조회 후 저장한다`() {
        val gallery = listOf(SpotImage("https://img/g1.jpg", null, "야경", CopyrightType.TYPE3))
        every { spotCachePort.getGallery(spotIdOf(100L)) } returns null
        every { tourInfoPort.findGallery(any()) } returns gallery

        val view = service.spotGallery(100L, DomainLocale.KO)

        assertEquals(1, view.size)
        assertEquals(CopyrightType.TYPE3.attributionText, view.single().attributionText)
        verify(exactly = 1) { spotCachePort.putGallery(spotIdOf(100L), gallery) }
    }

    @Test
    fun `갤러리 캐시 히트면 upstream을 호출하지 않는다`() {
        every { spotCachePort.getGallery(spotIdOf(100L)) } returns emptyList()

        assertTrue(service.spotGallery(100L, DomainLocale.KO).isEmpty())
        verify(exactly = 0) { tourInfoPort.findGallery(any()) }
    }

    // ── 지연 필드: 운영 정보 ─────────────────────────────────────────

    @Test
    fun `운영정보는 캐시 미스 시 조회 후 저장한다`() {
        every { spotCachePort.getIntro(spotIdOf(100L), SpotCategory.TOURIST_SPOT) } returns null
        every { tourInfoPort.findIntro(any(), SpotCategory.TOURIST_SPOT) } returns intro()

        val view = service.spotIntro(100L, SpotCategory.TOURIST_SPOT, DomainLocale.KO)

        assertEquals("09:00~18:00", view?.businessHours)
        verify(exactly = 1) { spotCachePort.putIntro(spotIdOf(100L), SpotCategory.TOURIST_SPOT, any()) }
    }

    @Test
    fun `운영정보가 값 없음으로 캐시돼 있으면 upstream을 다시 부르지 않는다`() {
        every {
            spotCachePort.getIntro(spotIdOf(100L), SpotCategory.TOURIST_SPOT)
        } returns CachedIntro(null)

        assertNull(service.spotIntro(100L, SpotCategory.TOURIST_SPOT, DomainLocale.KO))
        verify(exactly = 0) { tourInfoPort.findIntro(any(), any()) }
    }

    @Test
    fun `운영정보 category를 그대로 포트에 전달한다`() {
        every { spotCachePort.getIntro(any(), any()) } returns null
        every { tourInfoPort.findIntro(any(), SpotCategory("RESTAURANT")) } returns null

        service.spotIntro(100L, SpotCategory("RESTAURANT"), DomainLocale.KO)

        verify(exactly = 1) { tourInfoPort.findIntro(any(), SpotCategory("RESTAURANT")) }
    }

    // ── 지연 필드: 이용안내 ──────────────────────────────────────────

    @Test
    fun `이용안내는 캐시 미스 시 조회 후 저장한다`() {
        val items = listOf(SpotInfoItem("입장료", "무료"))
        every { spotCachePort.getInfoItems(spotIdOf(100L), SpotCategory.TOURIST_SPOT) } returns null
        every { tourInfoPort.findInfoItems(any(), SpotCategory.TOURIST_SPOT) } returns items

        val view = service.spotInfoItems(100L, SpotCategory.TOURIST_SPOT, DomainLocale.KO)

        assertEquals("입장료", view.single().name)
        verify(exactly = 1) {
            spotCachePort.putInfoItems(spotIdOf(100L), SpotCategory.TOURIST_SPOT, items)
        }
    }

    // ── 지연 필드: 좋아요 수 ─────────────────────────────────────────

    @Test
    fun `AC-14 - 좋아요 수는 캐시하지 않고 매번 집계한다`() {
        every { spotQueryPort.countLikes(100L, DomainLocale.KO) } returns 3L

        assertEquals(3L, service.spotLikeCount(100L, DomainLocale.KO))
        verify(exactly = 0) { spotCachePort.getDetail(any()) }
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

        assertEquals("LIKE", service.spotViewerReaction(100L, 7L, DomainLocale.EN))
        assertNull(service.spotViewerReaction(100L, 7L, DomainLocale.JA))
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

    // ── SPEC-0008 상세 본문 부재: 부재는 적재하고 성공, 실패는 적재하지 않고 표면화 ──

    @Test
    fun `AC-1 - 본문이 부재해도 예외 없이 SpotDetail을 반환한다`() {
        every { tourInfoPort.findDetail(any()) } returns null

        val view = service.spotDetail(100L, DomainLocale.KO)

        assertEquals(100L, view.id)
        assertEquals("경복궁", view.title)
    }

    @Test
    fun `AC-2 - 본문 부재는 SPOT_NOT_FOUND가 아니다`() {
        every { tourInfoPort.findDetail(any()) } returns null

        service.spotDetail(100L, DomainLocale.KO) // 예외가 나면 실패
    }

    @Test
    fun `AC-3 - 카탈로그 자체가 없으면 SPOT_NOT_FOUND다`() {
        every { spotQueryPort.findIdentity(404L, DomainLocale.KO) } returns null

        assertFailsWith<SpotDetailNotFoundException> { service.spotDetail(404L, DomainLocale.KO) }
    }

    @Test
    fun `AC-4 - 포트가 예외를 던지면 부재로 적재하지 않고 실패로 표면화한다`() {
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
        every { spotQueryPort.findSpotBasics(any(), any()) } returns basics(address = null)
        every { tourInfoPort.findDetail(any()) } returns null

        val view = service.spotDetail(100L, DomainLocale.KO)

        assertNull(view.address)
        assertEquals("경복궁", view.title)
    }

    @Test
    fun `AC-9 - 부재가 적재된 spot은 upstream을 다시 호출하지 않는다`() {
        every { spotDetailStorePort.findDetail(spotIdOf(100L)) } returns storedAbsence()

        val view = service.spotDetail(100L, DomainLocale.KO)

        assertNull(view.overview)
        verify(exactly = 0) { tourInfoPort.findDetail(any()) }
    }

    @Test
    fun `AC-10 - 한 번도 조회되지 않은 spot은 upstream을 호출한다`() {
        every { spotDetailStorePort.findDetail(spotIdOf(100L)) } returns null
        every { tourInfoPort.findDetail(any()) } returns detail()

        service.spotDetail(100L, DomainLocale.KO)

        verify(exactly = 1) { tourInfoPort.findDetail(any()) }
    }

    @Test
    fun `AC-11 - 부재와 미조회는 서로 다른 상태로 구분된다`() {
        // 호출 수는 카탈로그별 contentId로 센다.
        val unseen = SpotDetailQuery(contentId = "1", locale = DomainLocale.KO)
        val absent = SpotDetailQuery(contentId = "2", locale = DomainLocale.KO)

        // 미조회 → upstream을 친다.
        every { spotDetailStorePort.findDetail(spotIdOf(1L)) } returns null
        every { tourInfoPort.findDetail(any()) } returns detail()
        service.spotDetail(1L, DomainLocale.KO)
        verify(exactly = 1) { tourInfoPort.findDetail(unseen) }

        // 부재 적재됨 → 치지 않는다.
        every { spotDetailStorePort.findDetail(spotIdOf(2L)) } returns storedAbsence()
        service.spotDetail(2L, DomainLocale.KO)
        verify(exactly = 0) { tourInfoPort.findDetail(absent) }
    }

    @Test
    fun `SPEC-0037 AC-017 - 부재 행은 아무리 오래돼도 조회를 계기로 다시 묻지 않는다`() {
        // 재확보는 30일 주기 배치의 몫이다(SPEC-0037 FR-011).
        every { spotDetailStorePort.findDetail(spotIdOf(100L)) } returns storedAbsence(age = Duration.ofDays(400))

        val view = service.spotDetail(100L, DomainLocale.KO)

        assertNull(view.overview)
        verify(exactly = 0) { tourInfoPort.findDetail(any()) }
    }

    @Test
    fun `SPEC-0037 - 부재 행을 읽어도 적재 이벤트를 다시 발행하지 않는다`() {
        every { spotDetailStorePort.findDetail(spotIdOf(100L)) } returns storedAbsence(age = Duration.ofDays(40))

        service.spotDetail(100L, DomainLocale.KO)

        verify(exactly = 0) { eventPublisher.publish(any()) }
    }

    @Test
    fun `부재를 확인하면 적재 이벤트를 발행한다(R-3)`() {
        every { tourInfoPort.findDetail(any()) } returns null

        service.spotDetail(100L, DomainLocale.KO)

        verify(exactly = 1) { eventPublisher.publish(ofType(SpotDetailAbsent::class)) }
        verify(exactly = 0) { eventPublisher.publish(ofType(SpotDetailFetched::class)) }
    }

    @Test
    fun `캐시에 부재가 담겨 있으면 DB·upstream을 모두 건너뛴다`() {
        every { spotCachePort.getDetail(spotIdOf(100L)) } returns
            CachedDetail(storedAbsence().detail, bodyAbsent = true)

        val view = service.spotDetail(100L, DomainLocale.KO)

        assertNull(view.overview)
        assertEquals("경복궁", view.title)
        verify(exactly = 0) { spotDetailStorePort.findDetail(any()) }
        verify(exactly = 0) { tourInfoPort.findDetail(any()) }
    }

    @Test
    fun `DB의 부재 행을 읽으면 캐시도 부재로 충전한다`() {
        every { spotDetailStorePort.findDetail(spotIdOf(100L)) } returns storedAbsence()

        service.spotDetail(100L, DomainLocale.KO)

        verify(exactly = 1) { spotCachePort.putDetailAbsence(spotIdOf(100L), any()) }
        verify(exactly = 0) { spotCachePort.putDetail(any(), any()) }
    }

    // ── SPEC-0008 좋아요 목록 (R-15 ~ R-17) ──────────────────────────

    private fun likedSpot(id: Long, likedAt: Instant = Instant.parse("2026-08-01T00:00:00Z")) = LikedSpotView(
        id = id,
        title = "spot-$id",
        thumbnailImageUrl = "https://img/$id.jpg",
        address = null,
        category = SpotCategory.TOURIST_SPOT,
        copyright = CopyrightType.TYPE1,
        attributionText = CopyrightType.TYPE1.attributionText,
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

    @Test
    fun `좋아요 목록은 상세 조회 경로를 건드리지 않는다`() {
        // 목록은 spot 행만 본다(AC-55의 전제).
        every { spotQueryPort.findLikedSpots(any()) } returns likedPage(likedSpot(1L))

        service.likedSpots(LikedSpotsQuery(CategorySelection.UNFILTERED, 20, null), DomainLocale.KO, memberId = 7L)

        verify(exactly = 0) { spotDetailStorePort.findDetail(any()) }
        verify(exactly = 0) { tourInfoPort.findDetail(any()) }
        verify(exactly = 0) { spotCachePort.getDetail(any()) }
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

    @Test
    fun `상세 캐시·저장은 카탈로그 id가 아니라 원본 spot_id로 키를 만든다`() {
        val catalogId = 777L
        val spotId = catalogId + SPOT_ID_OFFSET
        every { spotQueryPort.findIdentity(catalogId, DomainLocale.JA) } returns
            SpotIdentity(
                spotId = spotId,
                contentId = "ta-999",
                mapX = CATALOG_MAP_X,
                mapY = CATALOG_MAP_Y,
            )
        every { tourInfoPort.findDetail(any()) } returns detail()

        service.spotDetail(catalogId, DomainLocale.JA)

        verify { spotCachePort.getDetail(spotId) }
        verify { spotDetailStorePort.findDetail(spotId) }
        verify(exactly = 0) { spotCachePort.getDetail(catalogId) }
        verify(exactly = 0) { spotDetailStorePort.findDetail(catalogId) }
    }

    private companion object {
        /** 카탈로그 id와 spot.id를 갈라 놓는 값. 같으면 뒤바뀐 코드가 통과한다. */
        const val SPOT_ID_OFFSET = 1_000_000L

        /** 기본 스텁의 카탈로그 좌표. 경도·위도를 서로 다른 값으로 둔다. */
        const val CATALOG_MAP_X = 127.0276
        const val CATALOG_MAP_Y = 37.4979

        /** 기본 스텁이 [catalogId]에 대해 돌려주는 원본 `spot.id`. */
        fun spotIdOf(catalogId: Long): Long = catalogId + SPOT_ID_OFFSET
    }
}
