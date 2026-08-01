package org.around.ieuri.tourism.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.tourism.application.exception.CategoryNotAvailableForLocaleException
import org.around.ieuri.tourism.application.exception.InvalidCoordinatesException
import org.around.ieuri.tourism.application.exception.RadiusTooLargeException
import org.around.ieuri.tourism.application.policy.SpotQueryPolicy
import org.around.ieuri.tourism.application.port.out.client.NearbySpotData
import org.around.ieuri.tourism.application.port.out.client.NearbySpotQuery
import org.around.ieuri.tourism.application.port.out.client.NearbySpotResult
import org.around.ieuri.tourism.application.port.out.client.SpotDetailQuery
import org.around.ieuri.tourism.application.port.out.client.TourInfoPort
import org.around.ieuri.tourism.application.port.out.query.SpotReactionQueryPort
import org.around.ieuri.tourism.application.query.SpotsQuery
import org.around.ieuri.tourism.domain.aggregate.SpotDetail
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.domain.vo.SpotImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** SpotQueryService — DTO→Domain 변환·거리계산·정렬(AC-1/2), 오프셋 페이지, 검증(AC-6/9), 가용성(AC-8). */
class SpotQueryServiceTest {

    private val tourInfoPort = mockk<TourInfoPort>()
    private val reactionQueryPort = mockk<SpotReactionQueryPort>()
    private val policy = SpotQueryPolicy(
        maxRadiusMeters = 100_000,
        maxPageSize = 100,
    )
    private val service = SpotQueryService(tourInfoPort, reactionQueryPort, policy)

    // 기본: 아무것도 반응하지 않음(빈 집합). 개별 테스트에서 override.
    init {
        every { reactionQueryPort.findReactedContentIds(any(), any(), any()) } returns emptySet()
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
        every { reactionQueryPort.findReactedContentIds(7L, any(), DomainLocale.KO) } returns setOf("b")

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
        verify(exactly = 0) { reactionQueryPort.findReactedContentIds(any(), any(), any()) }
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
        gallery = listOf(
            SpotImage("https://img/g1.jpg", null, "야경", CopyrightType.TYPE3),
        ),
        likeCount = likeCount,
    )

    @Test
    fun `spotDetail은 파생 필드와 실시간 likeCount를 합쳐 반환한다(AC-1)`() {
        every { tourInfoPort.findDetail(any()) } returns detail(likeCount = 0)
        every { reactionQueryPort.countLikes("100", DomainLocale.KO) } returns 3L

        val view = service.spotDetail("100", DomainLocale.KO)

        assertEquals("100", view.id)
        assertEquals("조선의 법궁", view.overview)
        assertEquals(CopyrightType.TYPE1.attributionText, view.attributionText)
        assertEquals(3, view.likeCount) // 실시간 집계값이 얹힌다
        assertEquals(1, view.gallery.size)
        assertEquals(CopyrightType.TYPE3.attributionText, view.gallery.single().attributionText)
    }

    @Test
    fun `likeCount는 캐시된 파생값이 아니라 매 조회 집계값을 쓴다(AC-14)`() {
        // 어댑터(캐시 포함)가 준 SpotDetail의 likeCount는 무시되고 countLikes가 진실이다.
        every { tourInfoPort.findDetail(any()) } returns detail(likeCount = 999)
        every { reactionQueryPort.countLikes("100", DomainLocale.KO) } returns 4L

        assertEquals(4, service.spotDetail("100", DomainLocale.KO).likeCount)
    }

    @Test
    fun `반응이 없으면 likeCount는 0(AC-12)`() {
        every { tourInfoPort.findDetail(any()) } returns detail()
        every { reactionQueryPort.countLikes(any(), any()) } returns 0L
        assertEquals(0, service.spotDetail("100", DomainLocale.KO).likeCount)
    }

    @Test
    fun `contentId와 locale을 그대로 포트에 전달한다`() {
        val captured = slot<SpotDetailQuery>()
        every { tourInfoPort.findDetail(capture(captured)) } returns detail()
        every { reactionQueryPort.countLikes(any(), any()) } returns 0L

        service.spotDetail("777", DomainLocale.JA)

        assertEquals("777", captured.captured.contentId)
        assertEquals(DomainLocale.JA, captured.captured.locale)
    }
}
