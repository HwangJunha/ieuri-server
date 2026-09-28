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
import org.around.ieuri.tourism.domain.vo.AreaName
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.MapCellDistrictRow
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
            row(1L, "11110"),
            row(2L, "41111"),
            row(3L, "41210"),
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
        every { mapCellQueryPort.findCellSpots(1L, DomainLocale.KO, 50) } returns emptyList()

        service.cellSpots(cellId = 1L, first = 999, locale = DomainLocale.KO)

        // 상한 50으로 잘린다(POL-018) — 페이지네이션이 없으니 +1을 읽지 않는다.
        verify { mapCellQueryPort.findCellSpots(1L, DomainLocale.KO, 50) }
    }

    /** 0 이하도 상한과 같은 방향으로 다룬다 — 오류가 아니라 최소 1건이다. */
    @Test
    fun `0 이하의 first는 1로 올라간다`() {
        every { mapCellQueryPort.findCellSpots(1L, DomainLocale.KO, 1) } returns emptyList()

        service.cellSpots(cellId = 1L, first = 0, locale = DomainLocale.KO)

        verify { mapCellQueryPort.findCellSpots(1L, DomainLocale.KO, 1) }
    }

    /** 조회 결과를 그대로 돌려준다 — 페이지 조립이 없다. */
    @Test
    fun `읽은 장소를 그대로 돌려준다`() {
        val rows = (1L..20L).map { spot(it, likeCount = 100 - it) }
        every { mapCellQueryPort.findCellSpots(1L, DomainLocale.KO, 20) } returns rows

        val spots = service.cellSpots(cellId = 1L, first = 20, locale = DomainLocale.KO)

        assertEquals(20, spots.size)
        assertEquals(1L, spots.first().id)
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

    private fun link(ownerId: Long) = MapShareLinkRecord(memberId = ownerId, token = "t", createdAt = Instant.parse("2026-08-21T00:00:00Z"))
}
