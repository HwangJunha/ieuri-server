package org.around.ieuri.tourism.application.relation

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.common.pagination.CursorEdge
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.CursorPageInfo
import org.around.ieuri.tourism.application.footprint.required.MapOwnerLookupPort
import org.around.ieuri.tourism.application.relation.provided.dto.MapRelationsQuery
import org.around.ieuri.tourism.application.relation.required.MapRelationOverlap
import org.around.ieuri.tourism.application.relation.required.MapRelationQueryPort
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapRelationQueryServiceTest {

    private val relationQueryPort = mockk<MapRelationQueryPort>()
    private val ownerLookupPort = mockk<MapOwnerLookupPort>()

    private val service = MapRelationQueryService(
        mapRelationQueryPort = relationQueryPort,
        mapOwnerLookupPort = ownerLookupPort,
        policy = MapRelationPolicy(maxPageSize = 50),
    )

    @Test
    fun `목록은 겹침과 닉네임을 합쳐 돌려준다`() {
        every { relationQueryPort.findRelations(10L, 20, null) } returns page(
            overlap(counterpartId = 20L, cells = listOf(1L, 2L, 3L), theirTotal = 9),
            overlap(counterpartId = 30L, cells = emptyList(), theirTotal = 4),
        )
        every { ownerLookupPort.findNicknames(listOf(20L, 30L)) } returns mapOf(20L to "여울", 30L to "바다")

        val page = service.mapRelations(MapRelationsQuery(first = 20, after = null), memberId = 10L)

        assertEquals(2, page.edges.size)
        val first = page.edges[0].node
        assertEquals(20L, first.memberId)
        assertEquals("여울", first.nickname)
        assertEquals(3, first.overlappingCount)
        assertEquals(listOf(1L, 2L, 3L), first.overlappingCellIds)
        assertEquals(9, first.theirVisitedCount)
        assertEquals("c-20", page.edges[0].cursor)

        val second = page.edges[1].node
        assertEquals(0, second.overlappingCount)
        assertEquals(emptyList(), second.overlappingCellIds)
    }

    @Test
    fun `닉네임을 얻지 못한 상대는 목록에서 빠진다`() {
        every { relationQueryPort.findRelations(10L, 20, null) } returns page(
            overlap(counterpartId = 20L, cells = listOf(1L), theirTotal = 1),
            overlap(counterpartId = 30L, cells = listOf(2L), theirTotal = 1),
        )
        // 탈퇴 직후 — 정리 리스너가 아직 관계를 지우지 않은 창.
        every { ownerLookupPort.findNicknames(listOf(20L, 30L)) } returns mapOf(20L to "여울")

        val page = service.mapRelations(MapRelationsQuery(first = 20, after = null), memberId = 10L)

        assertEquals(listOf(20L), page.edges.map { it.node.memberId })
    }

    @Test
    fun `탈퇴자가 걸러져도 다음 페이지 판정과 endCursor는 읽어 온 그대로다`() {
        // 살아남은 마지막 행으로 커서를 잡으면 다음 페이지가 이미 본 행부터 다시 시작한다.
        every { relationQueryPort.findRelations(10L, 2, null) } returns page(
            overlap(counterpartId = 20L, cells = listOf(1L), theirTotal = 1),
            overlap(counterpartId = 30L, cells = listOf(2L), theirTotal = 1),
            hasNextPage = true,
        )
        every { ownerLookupPort.findNicknames(listOf(20L, 30L)) } returns mapOf(30L to "바다")

        val page = service.mapRelations(MapRelationsQuery(first = 2, after = null), memberId = 10L)

        assertEquals(listOf(30L), page.edges.map { it.node.memberId })
        assertTrue(page.pageInfo.hasNextPage)
        assertEquals("c-30", page.pageInfo.endCursor)
        // startCursor는 남은 엣지에서 다시 뽑는다.
        assertEquals("c-30", page.pageInfo.startCursor)
    }

    @Test
    fun `끝자리 탈퇴자가 걸러져도 endCursor는 읽어 온 마지막 행을 가리킨다`() {
        // 살아남은 마지막 엣지로 커서를 잡으면 다음 페이지가 이미 본 행부터 다시 시작한다.
        every { relationQueryPort.findRelations(10L, 2, null) } returns page(
            overlap(counterpartId = 20L, cells = listOf(1L), theirTotal = 1),
            overlap(counterpartId = 30L, cells = listOf(2L), theirTotal = 1),
            hasNextPage = true,
        )
        every { ownerLookupPort.findNicknames(listOf(20L, 30L)) } returns mapOf(20L to "여울")

        val page = service.mapRelations(MapRelationsQuery(first = 2, after = null), memberId = 10L)

        assertEquals(listOf("c-20"), page.edges.map { it.cursor })
        assertEquals("c-30", page.pageInfo.endCursor)
        assertTrue(page.pageInfo.hasNextPage)
    }

    @Test
    fun `first는 상한으로 잘리고 오류가 아니다`() {
        every { relationQueryPort.findRelations(10L, 50, null) } returns page()
        every { ownerLookupPort.findNicknames(emptyList()) } returns emptyMap()

        service.mapRelations(MapRelationsQuery(first = 999, after = null), memberId = 10L)

        verify(exactly = 1) { relationQueryPort.findRelations(10L, 50, null) }
    }

    @Test
    fun `커서는 해석하지 않고 그대로 포트에 넘긴다`() {
        every { relationQueryPort.findRelations(10L, 20, "opaque") } returns page()
        every { ownerLookupPort.findNicknames(emptyList()) } returns emptyMap()

        val page = service.mapRelations(MapRelationsQuery(first = 20, after = "opaque"), memberId = 10L)

        verify(exactly = 1) { relationQueryPort.findRelations(10L, 20, "opaque") }
        assertTrue(page.pageInfo.hasPreviousPage)
    }

    /** 포트가 돌려주는 페이지. 커서는 `c-{상대 id}`로 둔다. */
    private fun page(vararg overlaps: MapRelationOverlap, hasNextPage: Boolean = false) = CursorPage(
        edges = overlaps.map { CursorEdge(cursor = "c-${it.counterpartId}", node = it) },
        pageInfo = CursorPageInfo(
            hasNextPage = hasNextPage,
            hasPreviousPage = true,
            startCursor = overlaps.firstOrNull()?.let { "c-${it.counterpartId}" },
            endCursor = overlaps.lastOrNull()?.let { "c-${it.counterpartId}" },
        ),
        totalCount = null,
    )

    private fun overlap(counterpartId: Long, cells: List<Long>, theirTotal: Int) = MapRelationOverlap(
        counterpartId = counterpartId,
        overlappingCellIds = cells,
        theirVisitedCount = theirTotal,
        createdAt = Instant.parse("2026-08-20T00:00:00Z"),
    )
}
