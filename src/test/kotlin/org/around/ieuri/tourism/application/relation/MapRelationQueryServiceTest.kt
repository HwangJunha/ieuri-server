package org.around.ieuri.tourism.application.relation

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.tourism.application.footprint.NationwideMapPolicy
import org.around.ieuri.tourism.application.footprint.required.MapOwnerLookupPort
import org.around.ieuri.tourism.application.relation.provided.dto.MapRelationOverlap
import org.around.ieuri.tourism.application.relation.provided.dto.MapRelationsQuery
import org.around.ieuri.tourism.application.relation.required.MapRelationQueryPort
import org.around.ieuri.tourism.application.shared.exception.InvalidCursorException
import org.around.ieuri.tourism.domain.relation.RelationCursor
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapRelationQueryServiceTest {

    private val relationQueryPort = mockk<MapRelationQueryPort>()
    private val ownerLookupPort = mockk<MapOwnerLookupPort>()

    private val service = MapRelationQueryService(
        mapRelationQueryPort = relationQueryPort,
        mapOwnerLookupPort = ownerLookupPort,
        policy = NationwideMapPolicy(maxCellSpotPageSize = 50, shareUrlBase = "https://www.ieuri.co.kr/tour/korea-map", maxRelationPageSize = 50),
    )

    @Test
    fun `목록은 겹침과 닉네임을 합쳐 돌려준다`() {
        every { relationQueryPort.findRelations(10L, 21, null) } returns listOf(
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

        val second = page.edges[1].node
        assertEquals(0, second.overlappingCount)
        assertEquals(emptyList(), second.overlappingCellIds)
    }

    @Test
    fun `닉네임을 얻지 못한 상대는 목록에서 빠진다`() {
        every { relationQueryPort.findRelations(10L, 21, null) } returns listOf(
            overlap(counterpartId = 20L, cells = listOf(1L), theirTotal = 1),
            overlap(counterpartId = 30L, cells = listOf(2L), theirTotal = 1),
        )
        // 탈퇴 직후 — 정리 리스너가 아직 관계를 지우지 않은 창.
        every { ownerLookupPort.findNicknames(listOf(20L, 30L)) } returns mapOf(20L to "여울")

        val page = service.mapRelations(MapRelationsQuery(first = 20, after = null), memberId = 10L)

        assertEquals(listOf(20L), page.edges.map { it.node.memberId })
    }

    @Test
    fun `탈퇴자가 섞여도 다음 페이지 존재 판정이 흔들리지 않는다`() {
        // hasNext는 거르기 전의 개수로 정한다.
        every { relationQueryPort.findRelations(10L, 3, null) } returns listOf(
            overlap(counterpartId = 20L, cells = listOf(1L), theirTotal = 1),
            overlap(counterpartId = 30L, cells = listOf(2L), theirTotal = 1),
            overlap(counterpartId = 40L, cells = listOf(3L), theirTotal = 1),
        )
        every { ownerLookupPort.findNicknames(listOf(20L, 30L)) } returns mapOf(20L to "여울")

        val page = service.mapRelations(MapRelationsQuery(first = 2, after = null), memberId = 10L)

        assertEquals(listOf(20L), page.edges.map { it.node.memberId })
        assertTrue(page.pageInfo.hasNextPage)
    }

    @Test
    fun `끝자리 탈퇴자가 잘려도 커서는 읽어 온 마지막을 가리킨다`() {
        // 살아남은 마지막 행으로 커서를 잡으면 다음 페이지가 이미 본 행부터 다시 시작한다.
        every { relationQueryPort.findRelations(10L, 21, null) } returns listOf(
            overlap(counterpartId = 20L, cells = listOf(1L), theirTotal = 1),
            overlap(counterpartId = 30L, cells = listOf(2L), theirTotal = 1),
        )
        every { ownerLookupPort.findNicknames(listOf(20L, 30L)) } returns mapOf(20L to "여울")

        val page = service.mapRelations(MapRelationsQuery(first = 20, after = null), memberId = 10L)

        assertEquals(RelationCursor(Instant.parse("2026-08-20T00:00:00Z"), 30L).encode(), page.pageInfo.endCursor)
    }

    @Test
    fun `first는 상한으로 잘리고 오류가 아니다`() {
        every { relationQueryPort.findRelations(10L, 51, null) } returns emptyList()
        every { ownerLookupPort.findNicknames(emptyList()) } returns emptyMap()

        service.mapRelations(MapRelationsQuery(first = 999, after = null), memberId = 10L)

        verify(exactly = 1) { relationQueryPort.findRelations(10L, 51, null) }
    }

    @Test
    fun `커서를 넘기면 그 지점 이후를 읽는다`() {
        val cursor = RelationCursor(Instant.parse("2026-08-01T00:00:00Z"), 20L).encode()
        every { relationQueryPort.findRelations(10L, 21, any()) } returns emptyList()
        every { ownerLookupPort.findNicknames(emptyList()) } returns emptyMap()

        service.mapRelations(MapRelationsQuery(first = 20, after = cursor), memberId = 10L)

        verify(exactly = 1) { relationQueryPort.findRelations(10L, 21, RelationCursor(Instant.parse("2026-08-01T00:00:00Z"), 20L)) }
    }

    @Test
    fun `형식이 어긋난 커서는 INVALID_CURSOR다`() {
        assertThrows<InvalidCursorException> { service.mapRelations(MapRelationsQuery(first = 20, after = "쓰레기"), memberId = 10L) }
    }

    private fun overlap(counterpartId: Long, cells: List<Long>, theirTotal: Int) = MapRelationOverlap(
        counterpartId = counterpartId,
        overlappingCellIds = cells,
        theirVisitedCount = theirTotal,
        createdAt = Instant.parse("2026-08-20T00:00:00Z"),
    )
}
