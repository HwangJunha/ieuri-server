package org.around.ieuri.tourism.application.service

import org.around.ieuri.tourism.application.port.out.query.SpotSearchResult
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.SpotCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 반경 조회 read DTO → 응답 뷰 필드별 매핑. attributionText는 copyright에서 파생된다. */
class SpotViewConversionsTest {

    @Test
    fun `모든 필드가 제자리로 옮겨지고 출처 문구가 파생된다`() {
        val view = SpotSearchResult(
            spotId = 21L,
            title = "해운대",
            address = "부산 해운대구",
            thumbnailImageUrl = "https://img/21.jpg",
            mapX = 129.16,
            mapY = 35.158,
            category = SpotCategory("LEISURE_SPORTS"),
            copyright = CopyrightType.TYPE3,
            distanceMeters = 321.0,
        ).toView()

        assertEquals(21L, view.id)
        assertEquals("해운대", view.title)
        assertEquals("부산 해운대구", view.address)
        assertEquals("https://img/21.jpg", view.thumbnailImageUrl)
        assertEquals(321.0, view.distanceMeters)
        assertEquals(SpotCategory("LEISURE_SPORTS"), view.category)
        assertEquals(CopyrightType.TYPE3, view.copyright)
        assertEquals(CopyrightType.TYPE3.attributionText, view.attributionText)
        assertEquals(129.16, view.mapX)
        assertEquals(35.158, view.mapY)
    }

    @Test
    fun `주소가 없으면 null이다`() {
        val view = SpotSearchResult(
            spotId = 1L, title = "t", address = null, thumbnailImageUrl = "u", mapX = 0.0, mapY = 0.0,
            category = SpotCategory.TOURIST_SPOT, copyright = CopyrightType.UNSPECIFIED, distanceMeters = 0.0,
        ).toView()
        assertNull(view.address)
    }
}
