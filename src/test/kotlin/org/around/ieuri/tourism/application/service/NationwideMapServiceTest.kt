package org.around.ieuri.tourism.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.tourism.application.exception.MapCellNotFoundException
import org.around.ieuri.tourism.application.exception.ShareLinkBlockedException
import org.around.ieuri.tourism.application.exception.ShareLinkNotFoundException
import org.around.ieuri.tourism.application.policy.NationwideMapPolicy
import org.around.ieuri.tourism.application.port.out.client.MapOwnerLookupPort
import org.around.ieuri.tourism.application.port.out.client.MapViewerBlockPort
import org.around.ieuri.tourism.application.port.out.persistence.MapShareLinkPort
import org.around.ieuri.tourism.application.port.out.persistence.MapShareLinkRecord
import org.around.ieuri.tourism.application.port.out.query.CellImageQueryPort
import org.around.ieuri.tourism.application.port.out.query.DistrictVisitQueryPort
import org.around.ieuri.tourism.application.port.out.query.MapCellDistrictQueryPort
import org.around.ieuri.tourism.application.port.out.query.MapCellQueryPort
import org.around.ieuri.tourism.application.query.CellSpotView
import org.around.ieuri.tourism.application.query.CellSpotsQuery
import org.around.ieuri.tourism.domain.vo.AreaName
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.LikeCountCursor
import org.around.ieuri.tourism.domain.vo.MapCellDistrictRow
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 전국 지도 조회(SPEC-0030 R6·R8·R11·R13·R15·R16). */
class NationwideMapServiceTest {

    private val mapCellQueryPort = mockk<MapCellQueryPort>()
    private val mapCellDistrictQueryPort = mockk<MapCellDistrictQueryPort>()
    private val districtVisitQueryPort = mockk<DistrictVisitQueryPort>()
    private val cellImageQueryPort = mockk<CellImageQueryPort>(relaxed = true)
    private val mapShareLinkPort = mockk<MapShareLinkPort>()
    private val mapOwnerLookupPort = mockk<MapOwnerLookupPort>()
    private val mapViewerBlockPort = mockk<MapViewerBlockPort>()

    private val service = NationwideMapService(
        mapCellQueryPort = mapCellQueryPort,
        mapCellDistrictQueryPort = mapCellDistrictQueryPort,
        districtVisitQueryPort = districtVisitQueryPort,
        cellImageQueryPort = cellImageQueryPort,
        mapShareLinkPort = mapShareLinkPort,
        mapOwnerLookupPort = mapOwnerLookupPort,
        mapViewerBlockPort = mapViewerBlockPort,
        policy = NationwideMapPolicy(
            maxCellSpotPageSize = 50,
            shareUrlBase = "https://www.ieuri.co.kr/tour/korea-map",
            maxRelationPageSize = 50,
        ),
    )

    // ── 칸 목록과 방문 여부 ────────────────────────────────────────────

    @Test
    fun `방문한 칸만 viewerVisited가 참이다`() {
        every { mapCellQueryPort.findAllCellDistricts() } returns listOf(
            row(1L, "11110"), row(2L, "41111"), row(3L, "41210"),
        )
        every { districtVisitQueryPort.findVisitedDistrictCodes(7L) } returns setOf("11110")

        val cells = service.mapCells(DomainLocale.KO, memberId = 7L)

        assertEquals(mapOf(1L to true, 2L to false, 3L to false), cells.associate { it.id to it.viewerVisited })
    }

    @Test
    fun `비인증 조회는 모든 칸이 미방문이고 방문 기록을 읽지 않는다`() {
        every { mapCellQueryPort.findAllCellDistricts() } returns listOf(row(1L, "11110"))

        val cells = service.mapCells(DomainLocale.KO, memberId = null)

        assertTrue(cells.none { it.viewerVisited })
        verify(exactly = 0) { districtVisitQueryPort.findVisitedDistrictCodes(any()) }
    }

    @Test
    fun `활성 언어가 없으면 EN, EN도 없으면 KO로 폴백한다`() {
        every { districtVisitQueryPort.findVisitedDistrictCodes(any()) } returns emptySet()
        every { mapCellQueryPort.findAllCellDistricts() } returns listOf(
            row(1L, "11110", name = AreaName(ko = "서울", en = "Seoul", ja = null, zh = null)),
        )

        assertEquals("Seoul", service.mapCells(DomainLocale.JA, 1L).single().name)

        every { mapCellQueryPort.findAllCellDistricts() } returns listOf(
            row(1L, "11110", name = AreaName(ko = "서울", en = "  ", ja = null, zh = null)),
        )
        assertEquals("서울", service.mapCells(DomainLocale.JA, 1L).single().name)
    }

    @Test
    fun `없는 칸을 조회하면 MAP_CELL_NOT_FOUND다`() {
        every { mapCellQueryPort.findCellDistricts(999L) } returns emptyList()

        assertFailsWith<MapCellNotFoundException> { service.mapCell(999L, DomainLocale.KO, null) }
    }

    // ── 칸 상세 spot ───────────────────────────────────────────────────

    @Test
    fun `상한을 넘는 first는 오류가 아니라 상한으로 잘린다`() {
        every { mapCellQueryPort.findCellSpots(1L, DomainLocale.KO, 51, null) } returns emptyList()

        service.cellSpots(CellSpotsQuery(1L, first = 999, after = null), DomainLocale.KO)

        // 상한 50 + 1 = 51건을 읽는다.
        verify { mapCellQueryPort.findCellSpots(1L, DomainLocale.KO, 51, null) }
    }

    @Test
    fun `first보다 한 건 더 읽어 다음 페이지를 판정한다`() {
        val rows = (1L..21L).map { spot(it, likeCount = 100 - it) }
        every { mapCellQueryPort.findCellSpots(1L, DomainLocale.KO, 21, null) } returns rows

        val page = service.cellSpots(CellSpotsQuery(1L, first = 20, after = null), DomainLocale.KO)

        assertEquals(20, page.edges.size)
        assertTrue(page.pageInfo.hasNextPage)
        assertFalse(page.pageInfo.hasPreviousPage)
    }

    // ── 칸 여러 개의 첫 페이지 (SPEC-0032 R24) ──────────────────────────

    /**
     * **161칸을 한 번에 읽는다.** 칸마다 `cellSpots`를 부르면 161회가 되는 것이 R24가 고치는
     * 문제이므로, 포트 호출이 정확히 한 번인 것이 이 테스트의 요지다.
     */
    @Test
    fun `여러 칸의 첫 페이지를 한 번의 조회로 읽는다`() {
        val cellIds = (1L..161L).toList()
        every { mapCellQueryPort.findFirstCellSpotsIn(cellIds, DomainLocale.KO, 21) } returns
            mapOf(1L to listOf(spot(1L, likeCount = 10)))

        service.firstCellSpotsOf(cellIds, first = 20, locale = DomainLocale.KO)

        verify(exactly = 1) { mapCellQueryPort.findFirstCellSpotsIn(cellIds, DomainLocale.KO, 21) }
    }

    /** 단건 경로와 같은 규칙이다 — 상한을 넘으면 오류가 아니라 절삭이다(POL-018). */
    @Test
    fun `배치에서도 상한을 넘는 first는 상한으로 잘린다`() {
        every { mapCellQueryPort.findFirstCellSpotsIn(any(), any(), 51) } returns emptyMap()

        service.firstCellSpotsOf(listOf(1L), first = 999, locale = DomainLocale.KO)

        verify { mapCellQueryPort.findFirstCellSpotsIn(listOf(1L), DomainLocale.KO, 51) }
    }

    /**
     * 칸마다 한 건씩 더 읽어 다음 페이지를 판정한다. 이 `+1` 규약이 어긋나면
     * `hasNextPage`가 마지막 페이지에서도 참이 되어 클라이언트가 영원히 페이지를 넘긴다.
     */
    @Test
    fun `배치도 first보다 한 건 더 읽어 칸마다 다음 페이지를 판정한다`() {
        every { mapCellQueryPort.findFirstCellSpotsIn(listOf(1L, 2L), DomainLocale.KO, 4) } returns mapOf(
            // 4건 = first(3) + 1 → 다음 페이지가 있다.
            1L to (1L..4L).map { spot(it, likeCount = 100 - it) },
            // 2건 → 다음 페이지가 없다.
            2L to (5L..6L).map { spot(it, likeCount = 100 - it) },
        )

        val pages = service.firstCellSpotsOf(listOf(1L, 2L), first = 3, locale = DomainLocale.KO)

        assertEquals(3, pages.getValue(1L).edges.size)
        assertTrue(pages.getValue(1L).pageInfo.hasNextPage)
        assertEquals(2, pages.getValue(2L).edges.size)
        assertFalse(pages.getValue(2L).pageInfo.hasNextPage)
    }

    /** 첫 페이지이므로 커서를 넘긴 적이 없다 — 양쪽 다 이전 페이지가 없다. */
    @Test
    fun `배치가 돌려주는 페이지는 이전 페이지가 없다`() {
        every { mapCellQueryPort.findFirstCellSpotsIn(any(), any(), any()) } returns
            mapOf(1L to listOf(spot(1L, likeCount = 10)))

        val pages = service.firstCellSpotsOf(listOf(1L), first = 20, locale = DomainLocale.KO)

        assertFalse(pages.getValue(1L).pageInfo.hasPreviousPage)
    }

    /**
     * **장소가 없는 칸은 결과에 키가 없다.** 빈 페이지로 채우는 것은 호출자(DataLoader)의
     * 몫이라는 규약을 고정한다 — 두 곳이 모두 채우거나 모두 안 채우면 필드가 null이 된다.
     */
    @Test
    fun `장소가 없는 칸은 결과에 키가 없다`() {
        every { mapCellQueryPort.findFirstCellSpotsIn(listOf(1L, 2L), DomainLocale.KO, 21) } returns
            mapOf(1L to listOf(spot(1L, likeCount = 10)))

        val pages = service.firstCellSpotsOf(listOf(1L, 2L), first = 20, locale = DomainLocale.KO)

        assertTrue(pages.containsKey(1L))
        assertFalse(pages.containsKey(2L))
    }

    // ── 공유 ───────────────────────────────────────────────────────────

    @Test
    fun `폐기되거나 없는 토큰은 SHARE_LINK_NOT_FOUND다`() {
        every { mapShareLinkPort.findActiveByToken("gone") } returns null

        assertFailsWith<ShareLinkNotFoundException> { service.sharedMap("gone", viewerMemberId = null) }
    }

    @Test
    fun `차단 관계면 SHARE_LINK_BLOCKED다`() {
        every { mapShareLinkPort.findActiveByToken("t") } returns link(ownerId = 1L)
        every { mapViewerBlockPort.isBlockedBetween(2L, 1L) } returns true

        assertFailsWith<ShareLinkBlockedException> { service.sharedMap("t", viewerMemberId = 2L) }
    }

    @Test
    fun `비인증 조회는 겹침이 null이고 차단을 묻지 않는다`() {
        every { mapShareLinkPort.findActiveByToken("t") } returns link(ownerId = 1L)
        every { mapOwnerLookupPort.findNickname(1L) } returns "여행자"
        every { districtVisitQueryPort.findVisitedDistrictCodes(1L) } returns setOf("11110")
        every { mapCellDistrictQueryPort.findCellIdsByDistrictCodes(setOf("11110")) } returns listOf(1L)

        val view = service.sharedMap("t", viewerMemberId = null)

        assertEquals("여행자", view.ownerNickname)
        assertEquals(listOf(1L), view.visitedCellIds)
        assertEquals(1, view.visitedCount)
        // 빈 배열(겹치는 칸 없음)과 구분된다.
        assertNull(view.overlappingCellIds)
        verify(exactly = 0) { mapViewerBlockPort.isBlockedBetween(any(), any()) }
    }

    @Test
    fun `인증 조회는 겹치는 칸을 함께 준다`() {
        every { mapShareLinkPort.findActiveByToken("t") } returns link(ownerId = 1L)
        every { mapViewerBlockPort.isBlockedBetween(2L, 1L) } returns false
        every { mapOwnerLookupPort.findNickname(1L) } returns "여행자"
        every { districtVisitQueryPort.findVisitedDistrictCodes(1L) } returns setOf("11110", "41111", "26110")
        every { districtVisitQueryPort.findVisitedDistrictCodes(2L) } returns setOf("11110", "50110")
        // 포트가 중복 제거·정렬을 보장한다(계약) — 스텁도 그 형태로 준다.
        every { mapCellDistrictQueryPort.findCellIdsByDistrictCodes(setOf("11110", "41111", "26110")) } returns listOf(1L, 3L, 2L)
        every { mapCellDistrictQueryPort.findCellIdsByDistrictCodes(setOf("11110", "50110")) } returns listOf(1L, 9L)

        val view = service.sharedMap("t", viewerMemberId = 2L)

        assertEquals(listOf(1L, 3L, 2L), view.visitedCellIds)
        assertEquals(listOf(1L), view.overlappingCellIds)
    }

    @Test
    fun `본인 링크는 차단을 묻지 않는다`() {
        every { mapShareLinkPort.findActiveByToken("t") } returns link(ownerId = 1L)
        every { mapOwnerLookupPort.findNickname(1L) } returns "나"
        every { districtVisitQueryPort.findVisitedDistrictCodes(1L) } returns emptySet()

        val view = service.sharedMap("t", viewerMemberId = 1L)

        assertEquals(emptyList(), view.visitedCellIds)
        assertEquals(emptyList(), view.overlappingCellIds)
        verify(exactly = 0) { mapViewerBlockPort.isBlockedBetween(any(), any()) }
    }

    private fun row(
        cellId: Long,
        districtCode: String,
        name: AreaName = AreaName(ko = "칸", en = "cell", ja = null, zh = null),
    ) = MapCellDistrictRow(
        cellId = cellId,
        districtCode = districtCode,
        cellName = name,
        provinceName = AreaName(ko = "시도", en = "province", ja = null, zh = null),
        provinceCode = districtCode.take(2),
        center = Coordinates(37.5, 127.0),
    )

    private fun spot(id: Long, likeCount: Long) = CellSpotView(
        id = id,
        title = "장소$id",
        thumbnailImageUrl = "https://img/$id.jpg",
        categoryCode = "TOURIST_SPOT",
        coordinates = Coordinates(37.5, 127.0),
        likeCount = likeCount,
    )

    private fun link(ownerId: Long) =
        MapShareLinkRecord(memberId = ownerId, token = "t", createdAt = Instant.parse("2026-08-21T00:00:00Z"))
}
