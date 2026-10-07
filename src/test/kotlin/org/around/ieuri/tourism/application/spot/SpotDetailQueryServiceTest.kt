package org.around.ieuri.tourism.application.spot

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.tourism.application.catalog.required.SpotBasics
import org.around.ieuri.tourism.application.catalog.required.SpotIdentity
import org.around.ieuri.tourism.application.catalog.required.SpotQueryPort
import org.around.ieuri.tourism.application.category.provided.exception.UnknownCategoryException
import org.around.ieuri.tourism.application.category.required.AppCategoryQueryPort
import org.around.ieuri.tourism.application.shared.exception.UpstreamUnavailableException
import org.around.ieuri.tourism.application.spot.provided.dto.KnownIntro
import org.around.ieuri.tourism.application.spot.provided.exception.SpotDetailNotFoundException
import org.around.ieuri.tourism.application.spot.provided.exception.SpotNotFoundInLocaleQueryException
import org.around.ieuri.tourism.application.spot.required.SpotCachePort
import org.around.ieuri.tourism.application.spot.required.SpotDetailQuery
import org.around.ieuri.tourism.application.spot.required.SpotDetailQueryPort
import org.around.ieuri.tourism.application.spot.required.SpotEventPublisherPort
import org.around.ieuri.tourism.application.spot.required.TourInfoPort
import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.spot.SpotDetail
import org.around.ieuri.tourism.domain.spot.SpotImage
import org.around.ieuri.tourism.domain.spot.SpotInfoItem
import org.around.ieuri.tourism.domain.spot.SpotIntro
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** SpotDetailQueryService — Valkey → DB → TourAPI 3단 폴백, 본문 부재 적재, 언어별 원본 해석, 지연 필드. */
class SpotDetailQueryServiceTest {

    private val tourInfoPort = mockk<TourInfoPort>()
    private val spotQueryPort = mockk<SpotQueryPort>()
    private val spotCachePort = mockk<SpotCachePort>()
    private val detailRepository = mockk<SpotDetailQueryPort>()
    private val eventPublisher = mockk<SpotEventPublisherPort>(relaxed = true)
    private val appCategoryQueryPort = mockk<AppCategoryQueryPort>()

    /** DB 단계는 실제 읽기 서비스에 목 포트를 물려 쓴다 — 단계별 스텁이 포트 수준에 그대로 남는다. */
    private val service = SpotDetailQueryService(
        read = SpotDetailReadService(spotQueryPort, detailRepository, appCategoryQueryPort),
        tourInfoPort = tourInfoPort,
        spotCachePort = spotCachePort,
        eventPublisher = eventPublisher,
    )

    init {
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
        // 기본: 캐시 미스 + 저장 성공. 캐시 히트 검증은 개별 테스트에서 override.
        every { spotCachePort.getDetail(any()) } returns null
        every { spotCachePort.putDetail(any(), any()) } returns Unit
        every { spotCachePort.putDetail(any(), any()) } returns Unit
        // 부재 응답 구성용 spot 값(R-2).
        every { spotQueryPort.findSpotBasics(any(), any()) } answers { basics() }
        every { spotCachePort.getGallery(any()) } returns null
        every { spotCachePort.putGallery(any(), any()) } returns Unit
        every { spotCachePort.getIntro(any(), any()) } returns null
        every { spotCachePort.putIntro(any(), any(), any()) } returns Unit
        every { spotCachePort.getInfoItems(any(), any()) } returns null
        every { spotCachePort.putInfoItems(any(), any(), any()) } returns Unit
        // 기본: DB도 미스. 3단 폴백의 단계별 검증은 개별 테스트에서 override.
        every { detailRepository.findDetail(any()) } returns null
        every { detailRepository.findGallery(any()) } returns null
        every { detailRepository.findIntro(any(), any()) } returns null
        every { detailRepository.findInfoItems(any(), any()) } returns null
    }

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
    private fun stored(detail: SpotDetail = detail()) = detail

    /** 적재된 부재 행. */
    private fun storedAbsence() = SpotDetail(
        title = "경복궁",
        category = SpotCategory.TOURIST_SPOT,
        thumbnailImageUrl = "https://img/100.jpg",
        overview = null,
        address = "서울 종로구",
        homepage = null,
        tel = null,
        copyright = CopyrightType.TYPE1,
    )

    private fun image() = SpotImage(
        imageUrl = "https://img/g1.jpg",
        thumbnailUrl = null,
        caption = null,
        copyright = CopyrightType.UNSPECIFIED,
    )

    // ---------- 3단 폴백: Valkey → DB → TourAPI (SPEC-0004 R-13) ----------

    @Test
    fun `Valkey 히트면 DB와 TourAPI를 모두 호출하지 않는다(AC-34)`() {
        // 캐시 키는 카탈로그 id(100)가 아니라 원본 spot.id다.
        every { spotCachePort.getDetail(spotIdOf(100L)) } returns detail()

        service.spotDetail(100L, DomainLocale.KO)

        verify(exactly = 0) { detailRepository.findDetail(any()) }
        verify(exactly = 0) { tourInfoPort.findDetail(any()) }
    }

    @Test
    fun `Valkey 미스에 DB 히트면 TourAPI를 치지 않고 Valkey를 충전한다(AC-35)`() {
        val spotId = spotIdOf(100L)
        every { detailRepository.findDetail(spotId) } returns stored()

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
        every { detailRepository.findDetail(spotIdOf(100L)) } returns stored()

        service.spotDetail(100L, DomainLocale.KO)

        verify(exactly = 0) { eventPublisher.publish(any()) }
    }

    @Test
    fun `갤러리도 같은 3단 구조를 갖는다`() {
        every { detailRepository.findGallery(spotIdOf(100L)) } returns listOf(image())

        val result = service.spotGallery(100L, DomainLocale.KO)

        assertEquals(1, result.size)
        verify(exactly = 0) { tourInfoPort.findGallery(any()) }
    }

    @Test
    fun `intro는 DB에 적재된 값이 null이어도 미스로 취급하지 않는다`() {
        every { detailRepository.findIntro(spotIdOf(100L), SpotCategory.TOURIST_SPOT) } returns
            KnownIntro(null)

        val result = service.spotIntro(100L, SpotCategory.TOURIST_SPOT, DomainLocale.KO)

        assertNull(result)
        verify(exactly = 0) { tourInfoPort.findIntro(any(), any()) }
    }

    @Test
    fun `infoItems도 같은 3단 구조를 갖는다`() {
        every { detailRepository.findInfoItems(spotIdOf(100L), SpotCategory.TOURIST_SPOT) } returns
            listOf(SpotInfoItem("주차", "가능"))

        val result = service.spotInfoItems(100L, SpotCategory.TOURIST_SPOT, DomainLocale.KO)

        assertEquals(1, result.size)
        verify(exactly = 0) { tourInfoPort.findInfoItems(any(), any()) }
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

    private fun detail() = SpotDetail(
        title = "경복궁",
        category = SpotCategory.TOURIST_SPOT,
        thumbnailImageUrl = "https://img/100.jpg",
        overview = "조선의 법궁",
        address = "서울 종로구",
        homepage = null,
        tel = null,
        copyright = CopyrightType.TYPE1,
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
        every { spotCachePort.getDetail(spotIdOf(100L)) } returns detail()

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
        every { spotCachePort.getDetail(spotIdOf(100L)) } returns detail()

        val view = service.spotDetail(100L, DomainLocale.KO)

        verify(exactly = 0) { tourInfoPort.findDetail(any()) }
        assertEquals(CATALOG_MAP_X, view.mapX)
        assertEquals(CATALOG_MAP_Y, view.mapY)
    }

    @Test
    fun `R1-2 - 본문이 DB에서 와도 좌표가 실린다`() {
        every { spotCachePort.getDetail(any()) } returns null
        every { detailRepository.findDetail(spotIdOf(100L)) } returns stored()

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
        every { spotCachePort.getDetail(any()) } returns detail()

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
        } returns KnownIntro(null)

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
        verify(exactly = 0) { spotCachePort.putDetail(any(), any()) }
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
        every { detailRepository.findDetail(spotIdOf(100L)) } returns storedAbsence()

        val view = service.spotDetail(100L, DomainLocale.KO)

        assertNull(view.overview)
        verify(exactly = 0) { tourInfoPort.findDetail(any()) }
    }

    @Test
    fun `AC-10 - 한 번도 조회되지 않은 spot은 upstream을 호출한다`() {
        every { detailRepository.findDetail(spotIdOf(100L)) } returns null
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
        every { detailRepository.findDetail(spotIdOf(1L)) } returns null
        every { tourInfoPort.findDetail(any()) } returns detail()
        service.spotDetail(1L, DomainLocale.KO)
        verify(exactly = 1) { tourInfoPort.findDetail(unseen) }

        // 부재 적재됨 → 치지 않는다.
        every { detailRepository.findDetail(spotIdOf(2L)) } returns storedAbsence()
        service.spotDetail(2L, DomainLocale.KO)
        verify(exactly = 0) { tourInfoPort.findDetail(absent) }
    }

    @Test
    fun `SPEC-0037 AC-017 - 부재 행은 아무리 오래돼도 조회를 계기로 다시 묻지 않는다`() {
        // 재확보는 30일 주기 배치의 몫이다(SPEC-0037 FR-011).
        every { detailRepository.findDetail(spotIdOf(100L)) } returns storedAbsence()

        val view = service.spotDetail(100L, DomainLocale.KO)

        assertNull(view.overview)
        verify(exactly = 0) { tourInfoPort.findDetail(any()) }
    }

    @Test
    fun `SPEC-0037 - 부재 행을 읽어도 적재 이벤트를 다시 발행하지 않는다`() {
        every { detailRepository.findDetail(spotIdOf(100L)) } returns storedAbsence()

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
            storedAbsence()

        val view = service.spotDetail(100L, DomainLocale.KO)

        assertNull(view.overview)
        assertEquals("경복궁", view.title)
        verify(exactly = 0) { detailRepository.findDetail(any()) }
        verify(exactly = 0) { tourInfoPort.findDetail(any()) }
    }

    @Test
    fun `DB의 부재 행을 읽으면 그 값으로 캐시를 충전한다`() {
        every { detailRepository.findDetail(spotIdOf(100L)) } returns storedAbsence()

        service.spotDetail(100L, DomainLocale.KO)

        verify(exactly = 1) { spotCachePort.putDetail(spotIdOf(100L), storedAbsence()) }
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
        verify { detailRepository.findDetail(spotId) }
        verify(exactly = 0) { spotCachePort.getDetail(catalogId) }
        verify(exactly = 0) { detailRepository.findDetail(catalogId) }
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
