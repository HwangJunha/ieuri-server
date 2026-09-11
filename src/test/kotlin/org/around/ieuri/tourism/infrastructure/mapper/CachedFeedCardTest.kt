package org.around.ieuri.tourism.infrastructure.mapper

import org.around.ieuri.tourism.application.port.out.query.SpotSearchResult
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.infrastructure.adapter.out.cache.CachedFeedCard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 추천 피드 캐시 카드 ↔ read DTO 양방향 필드별 매핑. mapX·mapY·distanceMeters가 같은 타입이라 값을 다르게 준다. */
class CachedFeedCardTest {

    private val card = SpotSearchResult(
        spotId = 31L,
        title = "광안리",
        address = "부산 수영구",
        thumbnailImageUrl = "https://img/31.jpg",
        mapX = 129.118,
        mapY = 35.153,
        category = SpotCategory("SHOPPING"),
        copyright = CopyrightType.TYPE1,
        distanceMeters = 777.0,
    )

    @Test
    fun `read DTO에서 캐시 DTO로`() {
        val cached = CachedFeedCard.from(card)
        assertEquals(31L, cached.id)
        assertEquals("광안리", cached.title)
        assertEquals("부산 수영구", cached.address)
        assertEquals("https://img/31.jpg", cached.thumbnailImageUrl)
        assertEquals(129.118, cached.mapX)
        assertEquals(35.153, cached.mapY)
        assertEquals("SHOPPING", cached.category)
        assertEquals(CopyrightType.TYPE1, cached.copyright)
        assertEquals(777.0, cached.distanceMeters)
    }

    @Test
    fun `캐시 DTO에서 read DTO로`() {
        val restored = CachedFeedCard(
            id = 31L, title = "광안리", address = "부산 수영구", thumbnailImageUrl = "https://img/31.jpg",
            mapX = 129.118, mapY = 35.153, category = "SHOPPING", copyright = CopyrightType.TYPE1, distanceMeters = 777.0,
        ).toSearchResult()
        assertEquals(31L, restored.spotId)
        assertEquals("광안리", restored.title)
        assertEquals("부산 수영구", restored.address)
        assertEquals("https://img/31.jpg", restored.thumbnailImageUrl)
        assertEquals(129.118, restored.mapX)
        assertEquals(35.153, restored.mapY)
        assertEquals(SpotCategory("SHOPPING"), restored.category)
        assertEquals(CopyrightType.TYPE1, restored.copyright)
        assertEquals(777.0, restored.distanceMeters)
    }

    @Test
    fun `주소가 없으면 양방향 모두 null이다`() {
        val cached = CachedFeedCard.from(card.copy(address = null))
        assertNull(cached.address)
        assertNull(cached.toSearchResult().address)
    }
}
