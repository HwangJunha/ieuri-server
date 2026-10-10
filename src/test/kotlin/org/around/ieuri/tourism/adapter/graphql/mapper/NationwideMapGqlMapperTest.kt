package org.around.ieuri.tourism.adapter.graphql.mapper

import org.around.ieuri.common.graphql.generated.types.RecordVisitsInput
import org.around.ieuri.common.graphql.generated.types.RemoveVisitInput
import org.around.ieuri.common.pagination.CursorEdge
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.CursorPageInfo
import org.around.ieuri.tourism.application.footprint.provided.dto.CellSpotView
import org.around.ieuri.tourism.application.footprint.provided.dto.MapShareLinkView
import org.around.ieuri.tourism.application.relation.provided.dto.MapRelationView
import org.around.ieuri.tourism.domain.shared.Coordinates
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/** GraphQL 경계 변환(POL-033). 위도·경도는 둘 다 Double이라 값을 다르게 주어 자리를 고정한다. */
class NationwideMapGqlMapperTest {

    @Test
    fun `칸 장소는 위도와 경도가 뒤바뀌지 않는다`() {
        val view = CellSpotView(
            id = 42L,
            title = "경복궁",
            thumbnailImageUrl = "https://img/thumb.jpg",
            categoryCode = "TOURIST_SPOT",
            // 서울: 위도 37.5, 경도 126.9 — 두 값이 확연히 다르다.
            coordinates = Coordinates(latitude = 37.5665, longitude = 126.9780),
            likeCount = 7L,
        )

        val gql = view.toGql()

        assertEquals(42L, gql.id)
        assertEquals("경복궁", gql.title)
        assertEquals("https://img/thumb.jpg", gql.thumbnailImageUrl)
        assertEquals("TOURIST_SPOT", gql.category.code)
        assertEquals(37.5665, gql.lat)
        assertEquals(126.9780, gql.lng)
    }

    @Test
    fun `방문 등록 입력은 회원 id를 인자에서 받는다`() {
        // 입력에 회원 id가 없다 — 인증 컨텍스트가 정한다.
        val command = RecordVisitsInput(districtCodes = listOf("11110", "41111")).toCommand(memberId = 7L)

        assertEquals(7L, command.memberId)
        assertEquals(listOf("11110", "41111"), command.districtCodes)
    }

    @Test
    fun `방문 해제 입력은 칸 id를 그대로 옮긴다`() {
        val command = RemoveVisitInput(cellId = 2L).toCommand(memberId = 9L)

        assertEquals(9L, command.memberId)
        assertEquals(2L, command.cellId)
    }

    @Test
    fun `빈 코드 목록도 변환된다`() {
        val command = RecordVisitsInput(districtCodes = emptyList()).toCommand(memberId = 1L)

        assertEquals(emptyList(), command.districtCodes)
    }

    @Test
    fun `도메인 좌표는 lat lng으로 이름이 바뀌어 나간다`() {
        // 도메인은 latitude/longitude, 스키마는 lat/lng다.
        val gql = Coordinates(latitude = 37.5665, longitude = 126.9780).toGql()

        assertEquals(37.5665, gql.lat)
        assertEquals(126.9780, gql.lng)
    }

    @Test
    fun `관계 1건은 필드마다 다른 값으로 옮겨진다`() {
        val gql = MapRelationView(
            memberId = 11L,
            nickname = "여울",
            overlappingCount = 3,
            overlappingCellIds = listOf(21L, 22L, 23L),
            theirVisitedCount = 9,
            createdAt = Instant.parse("2026-08-20T01:02:03Z"),
        ).toGql()

        assertEquals(11L, gql.memberId)
        assertEquals("여울", gql.nickname)
        assertEquals(3, gql.overlappingCount)
        assertEquals(listOf(21L, 22L, 23L), gql.overlappingCellIds)
        assertEquals(9, gql.theirVisitedCount)
        assertEquals("2026-08-20T01:02:03Z", gql.createdAt)
    }

    @Test
    fun `겹치는 칸이 없으면 빈 목록으로 나간다`() {
        val gql = MapRelationView(
            memberId = 12L,
            nickname = "바다",
            overlappingCount = 0,
            overlappingCellIds = emptyList(),
            theirVisitedCount = 0,
            createdAt = Instant.parse("2026-08-20T00:00:00Z"),
        ).toGql()

        assertEquals(0, gql.overlappingCount)
        assertEquals(emptyList(), gql.overlappingCellIds)
    }

    @Test
    fun `관계 커넥션은 엣지 커서와 경계 정보를 그대로 옮긴다`() {
        val view = MapRelationView(
            memberId = 11L,
            nickname = "여울",
            overlappingCount = 0,
            overlappingCellIds = emptyList(),
            theirVisitedCount = 0,
            createdAt = Instant.parse("2026-08-20T00:00:00Z"),
        )
        val page = CursorPage(
            edges = listOf(CursorEdge("c-11", view)),
            pageInfo = CursorPageInfo(hasNextPage = true, hasPreviousPage = false, startCursor = "c-11", endCursor = "c-99"),
            totalCount = null,
        )

        val gql = page.toGqlMapRelationConnection()

        assertEquals(listOf("c-11"), gql.edges.map { it.cursor })
        assertEquals(listOf(11L), gql.edges.map { it.node.memberId })
        assertEquals(true, gql.pageInfo.hasNextPage)
        assertEquals(false, gql.pageInfo.hasPreviousPage)
        assertEquals("c-11", gql.pageInfo.startCursor)
        assertEquals("c-99", gql.pageInfo.endCursor)
    }

    @Test
    fun `공유 링크는 주소와 생성 시각을 옮긴다`() {
        val gql = MapShareLinkView(shareUrl = "https://www.ieuri.co.kr/tour/korea-map/abc", createdAt = Instant.parse("2026-08-21T02:03:04Z")).toGql()

        assertEquals("https://www.ieuri.co.kr/tour/korea-map/abc", gql.shareUrl)
        assertEquals("2026-08-21T02:03:04Z", gql.createdAt)
    }
}
