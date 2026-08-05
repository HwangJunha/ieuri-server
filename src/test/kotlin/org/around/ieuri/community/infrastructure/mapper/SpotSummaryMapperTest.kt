package org.around.ieuri.community.infrastructure.mapper

import org.around.ieuri.community.domain.vo.PostTargetType
import org.around.ieuri.community.domain.vo.TargetCategory
import org.around.ieuri.community.infrastructure.adapter.out.client.mapper.toPostTargetView
import org.around.ieuri.tourism.application.query.SpotSummaryView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * tourism 응답 → 커뮤니티 read DTO 변환. 필드마다 서로 다른 값을 넣어 매핑이 뒤바뀌면
 * 실패하게 한다 — 통째로 비교하면 어느 필드가 틀렸는지 알 수 없다.
 */
class SpotSummaryMapperTest {

    @Test
    fun `필드마다 옮겨진다`() {
        val view = SpotSummaryView(
            id = 11094L,
            title = "경복궁",
            thumbnailImageUrl = "https://img/gyeongbok.jpg",
            categoryCode = "TOURIST_SPOT",
        ).toPostTargetView()

        assertEquals(PostTargetType.SPOT, view.type)
        assertEquals(11094L, view.id)
        assertEquals("경복궁", view.title)
        assertEquals("https://img/gyeongbok.jpg", view.thumbnailImageUrl)
        assertEquals(TargetCategory.TOURIST_SPOT, view.category)
    }

    @Test
    fun `언어 전용 카테고리도 옮겨진다`() {
        // 여행코스는 국문 전용, 교통은 외국어 전용이다. 둘 다 값 집합에 있어야 칩이 뜬다.
        assertEquals(
            TargetCategory.TRAVEL_COURSE,
            summary(categoryCode = "TRAVEL_COURSE").toPostTargetView().category,
        )
        assertEquals(
            TargetCategory.TRANSPORT,
            summary(categoryCode = "TRANSPORT").toPostTargetView().category,
        )
    }

    @Test
    fun `아홉 개 카테고리가 모두 옮겨진다`() {
        // 하나라도 빠지면 그 카테고리의 spot에 붙은 글이 칩으로 걸러지지 않는다.
        TargetCategory.entries.forEach { category ->
            assertEquals(
                category,
                summary(categoryCode = category.name).toPostTargetView().category,
                "${category.name}이 옮겨지지 않는다",
            )
        }
    }

    @Test
    fun `모르는 카테고리 코드는 null이다 - 칩으로 걸러지지 않을 뿐 조회는 성공한다`() {
        assertNull(summary(categoryCode = "UNKNOWN_CODE").toPostTargetView().category)
        assertNull(summary(categoryCode = "").toPostTargetView().category)
    }

    private fun summary(categoryCode: String) = SpotSummaryView(
        id = 1L,
        title = "대상",
        thumbnailImageUrl = "url",
        categoryCode = categoryCode,
    )
}
