package org.around.ieuri.tourism.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.tourism.application.exception.InvalidCoordinatesException
import org.around.ieuri.tourism.application.exception.RadiusTooLargeException
import org.around.ieuri.tourism.application.exception.SpotDetailNotFoundException
import org.around.ieuri.tourism.application.exception.SpotNotFoundInLocaleQueryException
import org.around.ieuri.tourism.application.exception.UnknownCategoryException
import org.around.ieuri.tourism.application.exception.UpstreamUnavailableException
import org.around.ieuri.tourism.application.policy.SpotQueryPolicy
import org.around.ieuri.common.context.AppKey
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
import org.around.ieuri.tourism.application.port.out.event.TourismEventPublisherPort
import org.around.ieuri.tourism.application.port.out.query.AppCategoryQueryPort
import org.around.ieuri.tourism.application.port.out.query.LikedSpotsSearch
import org.around.ieuri.tourism.application.port.out.query.NearbySpotSearch
import org.around.ieuri.tourism.application.port.out.query.SpotBasics
import org.around.ieuri.tourism.application.port.out.query.SpotIdentity
import org.around.ieuri.tourism.application.port.out.query.SpotQueryPort
import org.around.ieuri.tourism.application.port.out.query.SpotSpatialQueryPort
import org.around.ieuri.tourism.application.port.out.query.SpotSearchResult
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
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** SpotQueryService — DTO→Domain 변환·거리계산·정렬(AC-1/2), 오프셋 페이지, 검증(AC-6/9), 가용성(AC-8). */
class SpotQueryServiceTest {

    private val tourInfoPort = mockk<TourInfoPort>()
    private val spotQueryPort = mockk<SpotQueryPort>()
    private val spotSpatialQueryPort = mockk<SpotSpatialQueryPort>()
    private val spotCachePort = mockk<SpotCachePort>()
    private val spotDetailStorePort = mockk<SpotDetailStorePort>()
    private val eventPublisher = mockk<TourismEventPublisherPort>(relaxed = true)
    private val policy = SpotQueryPolicy(
        maxRadiusMeters = 100_000,
        maxPageSize = 100,
        maxLikedPageSize = 100,
        detailAbsenceTtl = Duration.ofDays(7),
        maxSearchPageSize = 50,
        maxSuggestionCount = 10,
        maxSearchQueryLength = 100,
        maxSearchPage = 500,
        minPopularLikeCount = 10,
    )
    private val appCategoryQueryPort = mockk<AppCategoryQueryPort>()
    private val service = SpotQueryService(
        tourInfoPort, spotQueryPort, spotSpatialQueryPort, spotCachePort, spotDetailStorePort,
        eventPublisher, appCategoryQueryPort, policy,
    )

    // 기본: 아무것도 반응하지 않음(빈 집합). 개별 테스트에서 override.
    init {
        every { spotQueryPort.findReactedSpotIds(any(), any()) } returns emptySet()
        // 기본: 정의된 카테고리로 둔다. intro·infoItems가 코드 유효성을 확인한다(SPEC-0020).
        every { appCategoryQueryPort.exists(any()) } returns true
        // (카탈로그 id, 언어) → contentId 해석. 기본은 어느 언어로 물어도 원본이 있다고 둔다.
        // 그 언어의 원본이 없는 경우는 개별 테스트에서 override한다.
        // spotId는 카탈로그 id와 **다른 값**을 준다 — 둘이 섞이면 캐시·spot_detail이 잘못된
        // id 공간을 쓰게 되는데, 같은 값을 쓰면 그 버그가 테스트를 통과한다.
        every { spotQueryPort.findIdentity(any(), any()) } answers {
            val catalogId = firstArg<Long>()
            SpotIdentity(
                spotId = catalogId + SPOT_ID_OFFSET,
                contentId = catalogId.toString(),
                language = secondArg(),
                mapX = CATALOG_MAP_X,
                mapY = CATALOG_MAP_Y,
            )
        }
        // 기본: 카탈로그 자체가 없다고 둔다 — 해석 실패의 기본 원인은 "그런 장소 없음"이다.
        // "카탈로그는 있는데 이 언어에만 없음"을 주장하는 테스트가 개별로 override한다.
        every { spotQueryPort.existsCatalog(any()) } returns false
        every { spotSpatialQueryPort.searchNearby(any()) } returns emptyList()
        every { spotSpatialQueryPort.countNearby(any()) } returns 0
        // 기본: 캐시 미스 + 저장 성공. 캐시 히트 검증은 개별 테스트에서 override.
        every { spotCachePort.getDetail(any()) } returns null
        every { spotCachePort.putDetail(any(), any()) } returns Unit
        every { spotCachePort.putDetailAbsence(any(), any()) } returns Unit
        // 부재 응답 구성용 spot 값(R-2). 부재를 다루지 않는 테스트에도 있어야 무해하다.
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
        longitude = 127.0,
        latitude = 37.5,
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

    /** 단일 카테고리 인자를 다중 선택으로 옮긴다. null이면 필터 없음. */
    private fun selection(category: SpotCategory?) =
        category?.let { CategorySelection.ofValues(setOf(it)) } ?: CategorySelection.UNFILTERED

    private fun query(page: Int = 1, size: Int = 100, category: SpotCategory? = null, radius: Int = 20000) =
        SpotsQuery(
            latitude = 37.5665, longitude = 126.9780,
            radiusMeters = radius, categories = selection(category), page = page, size = size,
        )

    @Test
    fun `DB가 돌려준 순서를 그대로 유지한다(AC-25)`() {
        // 정렬은 이제 DB(ORDER BY ST_Distance, id)가 담당한다. 서비스가 재정렬하면
        // DB의 타이브레이크가 무의미해지므로, 받은 순서를 보존하는 것이 올바른 동작이다.
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
        // 둘이 다른 기준을 쓰면 hasNext=false인데 totalCount는 더 있다고 말하는 상태가 생긴다
        // (런타임 계약 위험 3번).
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
        // 이 스펙의 핵심 주장 — 목록 조회 경로에서 upstream 의존이 완전히 사라진다.
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
        // 조회 후 걸러내면 페이지가 요청한 size보다 작아지므로 쿼리 조건으로 넘긴다.
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
        // 응답은 적재 완료를 기다리지 않는다 — 이벤트만 발행하고, 리스너가 @Async로 처리한다.
        verify(exactly = 1) { eventPublisher.publish(ofType(SpotDetailFetched::class)) }
    }

    @Test
    fun `DB 히트 시에는 이벤트를 발행하지 않는다`() {
        // 이미 적재된 것을 또 발행하면 큐가 불필요하게 찬다.
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
        // "적재됐으나 값 없음"을 미스로 오판하면 매 조회마다 upstream을 두드린다.
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

    /**
     * 노출 구성은 이제 `app_category`가 정한다(SPEC-0020) — 서비스는 앱·언어를 그대로 넘기고
     * 포트가 준 목록을 **가공하지 않는다**. 순서는 서버 계약이 아니므로 집합으로만 본다.
     */
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

    /** 다른 도메인이 쓰는 문자열 경로. 언어 태그를 도메인 언어로 읽고 카테고리를 이름으로 내려준다. */
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

    /** 정의가 없는 코드는 코드 자체를 라벨로 쓰는 폴백으로 채워 돌려준다 — 호출자가 분기하지 않게 한다. */
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

    /**
     * intro·infoItems는 코드가 캐시 키와 적재 행이 되므로 정의된 카테고리만 받는다(SPEC-0020).
     * 막지 않으면 비로그인 호출자가 임의 문자열마다 키와 빈 행을 하나씩 쌓을 수 있다.
     */
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
        businessHours = "09:00~18:00", closedDays = "화요일", parking = "가능",
        inquiryPhone = null, fee = null, petAllowed = null, creditCardAccepted = null,
    )

    @Test
    fun `spotDetail은 본문만 반환한다(AC-1)`() {
        every { tourInfoPort.findDetail(any()) } returns detail()

        val view = service.spotDetail(100L, DomainLocale.KO)

        // **뷰의 id는 클라이언트가 보낸 카탈로그 id다.** 내부 spot.id를 실으면 GraphQL의
        // SpotDetail.id로 내부 축이 새어 나가고, 지연 필드 리졸버가 그 값을 다시 카탈로그 id로
        // 되먹여 다른 장소를 조회하거나 SPOT_NOT_FOUND가 된다.
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

    // ── 상세 좌표(SPEC-0027 R1) ──────────────────────────────────────
    //
    // 본문은 캐시·DB·upstream·부재 폴백 네 경로로 갈리지만 **좌표의 출처는 카탈로그 하나뿐이다.**
    // 네 경로를 모두 세우는 이유가 그것이다 — 하나라도 다른 곳에서 좌표를 읽으면 그 경로만
    // 조용히 다른 값을 내보낸다.

    @Test
    fun `R1-1 - upstream 경로의 상세에 카탈로그 좌표가 실린다`() {
        every { tourInfoPort.findDetail(any()) } returns detail()

        val view = service.spotDetail(100L, DomainLocale.KO)

        assertEquals(CATALOG_MAP_X, view.mapX)
        assertEquals(CATALOG_MAP_Y, view.mapY)
    }

    @Test
    fun `R1-2 - 본문이 캐시에서 와도 좌표가 실린다`() {
        // **이 스펙의 핵심 회귀 지점.** 좌표를 캐시 DTO에 담는 구현이면 이미 캐시된 항목이
        // TTL 내내 기본값(0.0)을 돌려준다 — 캐시에 좌표가 없는 항목으로 세워야 그것이 잡힌다.
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
        // 폴백은 findSpotBasics로 제목·주소를 채우는데, 그 DTO에도 좌표 필드가 있다.
        // **좌표는 거기서 오면 안 된다** — basics()는 일부러 다른 값을 갖고 있어 출처가 갈리면 실패한다.
        every { tourInfoPort.findDetail(any()) } returns null

        val view = service.spotDetail(100L, DomainLocale.KO)

        assertEquals(CATALOG_MAP_X, view.mapX, "폴백 좌표도 카탈로그(identity)에서 와야 한다")
        assertEquals(CATALOG_MAP_Y, view.mapY, "폴백 좌표도 카탈로그(identity)에서 와야 한다")
    }

    @Test
    fun `R1-1 - 경도와 위도가 뒤바뀌지 않는다`() {
        // mapX가 경도, mapY가 위도다. 두 값이 같으면 뒤바뀐 매핑이 통과하므로 포트 응답을
        // 서로 다른 값으로 세우고 각각을 따로 판정한다.
        every { spotQueryPort.findIdentity(100L, DomainLocale.KO) } returns SpotIdentity(
            spotId = spotIdOf(100L),
            contentId = "100",
            language = DomainLocale.KO,
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
        // 외부 식별자(카탈로그 id)와 upstream 식별자(contentId)는 별개다 — 그대로 넘기는 것이
        // 아니라 (카탈로그, 언어)로 해석해서 넘긴다.
        every { spotQueryPort.findIdentity(777L, DomainLocale.JA) } returns
            SpotIdentity(
                spotId = 777L + SPOT_ID_OFFSET,
                contentId = "ta-999",
                language = DomainLocale.JA,
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
    fun `원본 언어가 요청 언어와 달라도 그 원본의 언어로 upstream에 묻는다`() {
        // **회귀 방지.** 번역으로 격차를 메운 카탈로그는 그 언어의 원본이 없다(실측 36,000건
        // 이상) — 포트가 다른 언어 원본으로 폴백해 돌려준다. 그때 요청 언어로 upstream에
        // 물으면 그 contentId가 없어 실패한다. **돌려받은 language로 물어야 한다.**
        every { spotQueryPort.findIdentity(777L, DomainLocale.JA) } returns
            SpotIdentity(
                spotId = 777L + SPOT_ID_OFFSET,
                contentId = "ko-123",
                language = DomainLocale.KO,
                mapX = CATALOG_MAP_X,
                mapY = CATALOG_MAP_Y,
            )
        val captured = slot<SpotDetailQuery>()
        every { tourInfoPort.findDetail(capture(captured)) } returns detail()

        service.spotDetail(777L, DomainLocale.JA)

        assertEquals("ko-123", captured.captured.contentId)
        assertEquals(DomainLocale.KO, captured.captured.locale, "원본 언어로 물어야 한다")
    }

    @Test
    fun `원본이 하나도 없는 카탈로그는 거부한다`() {
        // 폴백해도 원본이 없으면 상세를 물을 곳이 없다 — 상류에서 장소가 사라진 경우다.
        every { spotQueryPort.findIdentity(777L, DomainLocale.KO) } returns null

        assertFailsWith<SpotDetailNotFoundException> { service.spotDetail(777L, DomainLocale.KO) }
        verify(exactly = 0) { tourInfoPort.findDetail(any()) }
    }

    @Test
    fun `캐시가 히트해도 그 언어의 원본이 없으면 거부한다`() {
        // **회귀 방지.** 언어 판정을 upstream 폴백 직전에 두면 캐시 히트에서 그대로 통과해,
        // 정작 가장 흔한 경로에서 교차 언어 콘텐츠가 나간다. spot_detail과 캐시 키 모두 언어를
        // 들지 않으므로(V20) 그 단계에서는 스스로 판별할 수 없다.
        every { spotQueryPort.findIdentity(777L, DomainLocale.KO) } returns null
        // 카탈로그는 있고 이 언어에만 원본이 없다 — 두 부재를 다른 코드로 가른다(AC-12.3).
        every { spotQueryPort.existsCatalog(777L) } returns true
        // 캐시 키가 어느 축이든 히트하도록 둔다 — 해석이 캐시보다 먼저 걸린다는 것이 요지다.
        every { spotCachePort.getDetail(any()) } returns CachedDetail(detail(), bodyAbsent = false)

        assertFailsWith<SpotNotFoundInLocaleQueryException> { service.spotDetail(777L, DomainLocale.KO) }
    }

    @Test
    fun `지연 필드도 그 언어의 원본이 없으면 거부한다`() {
        // 갤러리·운영정보·이용안내가 각각 독립 진입점이라 전부 검증해야 한다 —
        // 하나라도 빠지면 그 필드만 교차 언어로 새어 나간다.
        every { spotQueryPort.findIdentity(777L, DomainLocale.KO) } returns null
        every { spotQueryPort.existsCatalog(777L) } returns true
        // 캐시에 값이 있어도 해석 실패가 먼저 걸려야 한다 — 캐시 키가 어느 축이든 무관하게.
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
        // 히트 판정은 래퍼로 한다 — value가 null이라고 미스로 취급하면 매번 upstream을 두드리게 된다.
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
        // 캐시 포트는 좋아요에 관여하지 않는다.
        verify(exactly = 0) { spotCachePort.getDetail(any()) }
    }

    @Test
    fun `AC-12 - 반응이 없으면 좋아요 수는 0`() {
        every { spotQueryPort.countLikes(any(), any()) } returns 0L
        assertEquals(0L, service.spotLikeCount(100L, DomainLocale.KO))
    }

    @Test
    fun `AC-13_5 - 좋아요 수는 활성 언어 안에서만 센다`() {
        // 언어가 집계 축의 일부다 — 같은 카탈로그라도 언어마다 다른 값이 나온다.
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
        // 영어로 반응한 장소가 일본어 큐에서도 빠지면 그 언어에서 한 번도 보지 못한 장소를 잃는다.
        val captured = slot<NearbySpotSearch>()
        every { spotQueryPort.findReactedSpotIds(7L, DomainLocale.JA) } returns setOf(9L)
        every { spotSpatialQueryPort.searchNearby(capture(captured)) } returns emptyList()

        service.spots(query(), DomainLocale.JA, memberId = 7L)

        assertEquals(setOf(9L), captured.captured.excludedSpotIds)
        verify { spotQueryPort.findReactedSpotIds(7L, DomainLocale.JA) }
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

        // 부재 경로에서도 뷰의 id는 클라이언트가 보낸 카탈로그 id다(내부 spot.id가 아니다).
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
    fun `AC-3 - 카탈로그 자체가 없으면 SPOT_NOT_FOUND다`() {
        // 본문 부재와 id 미해석은 서로 다른 경로다.
        every { spotQueryPort.findIdentity(404L, DomainLocale.KO) } returns null

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
        every { spotQueryPort.findSpotBasics(any(), any()) } returns basics(address = null)
        every { tourInfoPort.findDetail(any()) } returns null

        val view = service.spotDetail(100L, DomainLocale.KO)

        assertNull(view.address)
        assertEquals("경복궁", view.title)
    }

    @Test
    fun `AC-9 - 부재가 적재된 spot은 upstream을 다시 호출하지 않는다`() {
        // 부재 적재의 존재 이유 그 자체 — 없는 데이터를 매 조회마다 다시 묻지 않는다.
        every { spotDetailStorePort.findDetail(spotIdOf(100L)) } returns storedAbsence()

        val view = service.spotDetail(100L, DomainLocale.KO)

        assertNull(view.overview)
        verify(exactly = 0) { tourInfoPort.findDetail(any()) }
    }

    @Test
    fun `AC-10 - 한 번도 조회되지 않은 spot은 upstream을 호출한다`() {
        // 행 없음(미조회)과 행 있음+부재는 다른 상태다.
        every { spotDetailStorePort.findDetail(spotIdOf(100L)) } returns null
        every { tourInfoPort.findDetail(any()) } returns detail()

        service.spotDetail(100L, DomainLocale.KO)

        verify(exactly = 1) { tourInfoPort.findDetail(any()) }
    }

    @Test
    fun `AC-11 - 부재와 미조회는 서로 다른 상태로 구분된다`() {
        // 두 카탈로그가 각자의 contentId로 upstream을 묻기 때문에, 호출 수는 카탈로그별로 센다 —
        // any()로 세면 서로 다른 두 조회가 한 카운터에 합쳐진다.
        val unseen = SpotDetailQuery(contentId = "1", locale = DomainLocale.KO)
        val absent = SpotDetailQuery(contentId = "2", locale = DomainLocale.KO)

        // 미조회 → upstream을 친다.
        every { spotDetailStorePort.findDetail(spotIdOf(1L)) } returns null
        every { tourInfoPort.findDetail(any()) } returns detail()
        service.spotDetail(1L, DomainLocale.KO)
        verify(exactly = 1) { tourInfoPort.findDetail(unseen) }

        // 부재 적재됨 → 치지 않는다. 둘을 같은 null로 표현했다면 이 구분이 불가능하다.
        every { spotDetailStorePort.findDetail(spotIdOf(2L)) } returns storedAbsence()
        service.spotDetail(2L, DomainLocale.KO)
        verify(exactly = 0) { tourInfoPort.findDetail(absent) }
    }

    @Test
    fun `AC-12 - 신선도 기준 이전이면 여전히 부재로 응답한다`() {
        every { spotDetailStorePort.findDetail(spotIdOf(100L)) } returns storedAbsence(age = Duration.ofDays(3))

        val view = service.spotDetail(100L, DomainLocale.KO)

        assertNull(view.overview)
        verify(exactly = 0) { tourInfoPort.findDetail(any()) }
    }

    @Test
    fun `AC-13 - 부재의 신선도가 지나면 upstream에 다시 묻는다`() {
        every { spotDetailStorePort.findDetail(spotIdOf(100L)) } returns storedAbsence(age = Duration.ofDays(8))
        every { tourInfoPort.findDetail(any()) } returns null

        service.spotDetail(100L, DomainLocale.KO)

        verify(exactly = 1) { tourInfoPort.findDetail(any()) }
    }

    @Test
    fun `AC-14 - 재조회 결과 본문이 생기면 본문 있는 상태로 전환된다`() {
        every { spotDetailStorePort.findDetail(spotIdOf(100L)) } returns storedAbsence(age = Duration.ofDays(8))
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
        every { spotDetailStorePort.findDetail(spotIdOf(100L)) } returns storedAbsence(age = Duration.ofDays(8))
        every { tourInfoPort.findDetail(any()) } returns null

        service.spotDetail(100L, DomainLocale.KO)

        // 부재 이벤트 재발행 = 어댑터의 upsert가 fetched_at을 now()로 갱신 → 창이 새로 시작된다.
        verify(exactly = 1) { eventPublisher.publish(ofType(SpotDetailAbsent::class)) }
    }

    @Test
    fun `AC-16 - 재조회가 실패로 끝나면 적재 시각을 갱신하지 않는다`() {
        // 실패는 부재의 재확인이 아니므로 신선도 창을 연장해서는 안 된다.
        every { spotDetailStorePort.findDetail(spotIdOf(100L)) } returns storedAbsence(age = Duration.ofDays(8))
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
        // 부재를 일반 본문으로 캐시하면 캐시 히트 시 bodyAbsent가 false로 되살아난다.
        every { spotDetailStorePort.findDetail(spotIdOf(100L)) } returns storedAbsence()

        service.spotDetail(100L, DomainLocale.KO)

        verify(exactly = 1) { spotCachePort.putDetailAbsence(spotIdOf(100L), any()) }
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
        // 본인 것만·요청 언어만이라는 두 제약이 쿼리 단계에서 걸려야 한다. 조회 후 걸러내면
        // 페이지가 요청한 크기보다 작아진다.
        val captured = slot<LikedSpotsSearch>()
        every { spotQueryPort.findLikedSpots(capture(captured)) } returns likedPage()

        service.likedSpots(LikedSpotsQuery(categories = CategorySelection.UNFILTERED, first = 20, after = null), DomainLocale.EN, memberId = 7L)

        assertEquals(7L, captured.captured.memberId)
        assertEquals(DomainLocale.EN, captured.captured.language)
    }

    @Test
    fun `AC-45 - 포트가 돌려준 목록을 그대로 반환한다`() {
        // LIKE 필터·정렬은 포트(쿼리)의 책임이다 — 서비스가 재정렬하면 DB의 타이브레이크가
        // 무의미해진다(spots와 같은 규율).
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
        // 목록은 spot 행만 본다 — 상세가 부재한 spot도 정상적으로 나타나야 하므로
        // spot_detail·캐시·upstream 어느 것도 타지 않는다(AC-55의 전제).
        every { spotQueryPort.findLikedSpots(any()) } returns likedPage(likedSpot(1L))

        service.likedSpots(LikedSpotsQuery(CategorySelection.UNFILTERED, 20, null), DomainLocale.KO, memberId = 7L)

        verify(exactly = 0) { spotDetailStorePort.findDetail(any()) }
        verify(exactly = 0) { tourInfoPort.findDetail(any()) }
        verify(exactly = 0) { spotCachePort.getDetail(any()) }
    }
    @Test
    fun `상세 캐시·저장은 카탈로그 id가 아니라 원본 spot_id로 키를 만든다`() {
        // **두 id 공간이 섞이면 조용히 틀린다.** spot_detail 계열의 FK가 spot(id)를 가리키므로
        // 카탈로그 id로 적재하면 FK 위반으로 실패하고(리스너가 삼킨다), 값이 우연히 겹치면
        // 다른 장소의 본문이 적재된다. 캐시도 같은 이유로 영구 미스가 된다.
        val catalogId = 777L
        val spotId = catalogId + SPOT_ID_OFFSET
        every { spotQueryPort.findIdentity(catalogId, DomainLocale.JA) } returns
            SpotIdentity(
                spotId = spotId,
                contentId = "ta-999",
                language = DomainLocale.JA,
                mapX = CATALOG_MAP_X,
                mapY = CATALOG_MAP_Y,
            )
        every { tourInfoPort.findDetail(any()) } returns detail()

        service.spotDetail(catalogId, DomainLocale.JA)

        // 캐시 조회·적재가 전부 spot.id로 이뤄져야 한다.
        verify { spotCachePort.getDetail(spotId) }
        verify { spotDetailStorePort.findDetail(spotId) }
        verify(exactly = 0) { spotCachePort.getDetail(catalogId) }
        verify(exactly = 0) { spotDetailStorePort.findDetail(catalogId) }
    }

    private companion object {
        /**
         * 테스트에서 카탈로그 id와 spot.id를 갈라 놓는 값. 두 id가 같으면 서로 뒤바뀐 코드가
         * 테스트를 통과한다 — 실제로 그 버그가 있었다.
         */
        const val SPOT_ID_OFFSET = 1_000_000L

        /**
         * 기본 스텁의 카탈로그 좌표. **경도·위도를 서로 다른 값으로 둔다** — 같으면 mapX/mapY가
         * 뒤바뀐 매핑이 테스트를 통과한다.
         */
        const val CATALOG_MAP_X = 127.0276
        const val CATALOG_MAP_Y = 37.4979

        /** 기본 스텁이 [catalogId]에 대해 돌려주는 원본 `spot.id`. */
        fun spotIdOf(catalogId: Long): Long = catalogId + SPOT_ID_OFFSET
    }
}
