package org.around.ieuri.tourism.infrastructure.mapper

import org.around.ieuri.common.graphql.generated.types.RecordVisitsInput
import org.around.ieuri.common.graphql.generated.types.RemoveVisitInput
import org.around.ieuri.tourism.application.query.CellSpotView
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql.mapper.toCommand
import org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql.mapper.toGql
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * GraphQL 경계 변환(POL-033).
 *
 * **위도·경도는 둘 다 Double이라 뒤바뀌어도 컴파일된다** — 값을 다르게 주어 자리를 고정한다.
 */
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
        // 입력에 회원 id가 없다는 것이 계약이다 — 클라이언트가 "누구의 기록인지"를 주장할 수 없다.
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
}
