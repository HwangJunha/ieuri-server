package org.around.ieuri.community.infrastructure.mapper

import org.around.ieuri.community.adapter.client.mapper.toPostSpotView
import org.around.ieuri.community.domain.shared.TargetCategory
import org.around.ieuri.tourism.application.query.SpotSummaryView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** tourism 응답 → 커뮤니티 read DTO 변환. 필드마다 서로 다른 값을 넣는다. */
class SpotSummaryMapperTest {

    @Test
    fun `필드마다 옮겨진다`() {
        // id는 언어별 spot.id가 아니라 카탈로그 id다(SPEC-0019 R-12).
        val view = SpotSummaryView(
            id = 11094L,
            title = "경복궁",
            thumbnailImageUrl = "https://img/gyeongbok.jpg",
            categoryCode = "TOURIST_SPOT",
            longitude = 126.9770,
            latitude = 37.5796,
        ).toPostSpotView()

        assertEquals(11094L, view.id)
        assertEquals("경복궁", view.title)
        assertEquals("https://img/gyeongbok.jpg", view.thumbnailImageUrl)
        assertEquals(TargetCategory.TOURIST_SPOT, view.category)
        // 좌표는 글 적재 시 복사된다.
        assertEquals(126.9770, view.longitude)
        assertEquals(37.5796, view.latitude)
    }

    @Test
    fun `언어 전용 카테고리도 옮겨진다`() {
        assertEquals(
            TargetCategory.TRAVEL_COURSE,
            summary(categoryCode = "TRAVEL_COURSE").toPostSpotView().category,
        )
        assertEquals(
            TargetCategory.TRANSPORT,
            summary(categoryCode = "TRANSPORT").toPostSpotView().category,
        )
    }

    @Test
    fun `아홉 개 카테고리가 모두 옮겨진다`() {
        TargetCategory.entries.forEach { category ->
            assertEquals(
                category,
                summary(categoryCode = category.name).toPostSpotView().category,
                "${category.name}이 옮겨지지 않는다",
            )
        }
    }

    @Test
    fun `모르는 카테고리 코드는 null이다 - 칩으로 걸러지지 않을 뿐 조회는 성공한다`() {
        assertNull(summary(categoryCode = "UNKNOWN_CODE").toPostSpotView().category)
        assertNull(summary(categoryCode = "").toPostSpotView().category)
    }

    private fun summary(categoryCode: String) = SpotSummaryView(
        id = 1L,
        title = "대상",
        thumbnailImageUrl = "url",
        categoryCode = categoryCode,
        longitude = 127.0,
        latitude = 37.5,
    )
}
