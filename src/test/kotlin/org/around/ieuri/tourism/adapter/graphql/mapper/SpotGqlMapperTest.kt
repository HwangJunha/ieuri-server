package org.around.ieuri.tourism.adapter.graphql.mapper

import org.around.ieuri.common.pagination.OffsetPage
import org.around.ieuri.tourism.application.catalog.provided.dto.SpotView
import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.around.ieuri.tourism.domain.spot.SpotImage
import org.around.ieuri.tourism.domain.spot.SpotInfoItem
import org.around.ieuri.tourism.domain.spot.SpotIntro
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.around.ieuri.common.graphql.generated.types.CopyrightType as GqlCopyrightType

/** `SpotView` → GraphQL `Spot` 필드별 매핑. mapX·mapY·distanceMeters가 모두 Double이라 값을 다르게 준다. */
class SpotGqlMapperTest {

    @Test
    fun `모든 필드가 제자리로 옮겨진다`() {
        val gql = SpotView(
            id = 11L,
            title = "경복궁",
            address = "서울 종로구",
            thumbnailImageUrl = "https://img/11.jpg",
            distanceMeters = 123.4,
            category = SpotCategory("CULTURAL_FACILITY"),
            copyright = CopyrightType.TYPE1,
            mapX = 126.977,
            mapY = 37.579,
        ).toGql()

        assertEquals(11L, gql.id)
        assertEquals("경복궁", gql.title)
        assertEquals("서울 종로구", gql.address)
        assertEquals("https://img/11.jpg", gql.thumbnailImageUrl)
        assertEquals(123.4, gql.distanceMeters)
        assertEquals("CULTURAL_FACILITY", gql.category.code)
        assertEquals(GqlCopyrightType.TYPE1, gql.copyright)
        assertEquals(CopyrightType.TYPE1.attributionText, gql.attributionText)
        assertEquals(126.977, gql.mapX)
        assertEquals(37.579, gql.mapY)
    }

    @Test
    fun `주소가 없으면 null이다`() {
        val gql = SpotView(
            id = 1L, title = "t", address = null, thumbnailImageUrl = "u", distanceMeters = 0.0,
            category = SpotCategory.TOURIST_SPOT, copyright = CopyrightType.UNSPECIFIED,
            mapX = 0.0, mapY = 0.0,
        ).toGql()
        assertNull(gql.address)
    }

    private fun card(id: Long) = SpotView(
        id = id, title = "t$id", address = null, thumbnailImageUrl = "u$id", distanceMeters = id.toDouble(),
        category = SpotCategory.TOURIST_SPOT, copyright = CopyrightType.UNSPECIFIED, mapX = 1.0, mapY = 2.0,
    )

    @Test
    fun `오프셋 페이지는 항목 순서와 페이지 값을 그대로 옮긴다`() {
        val page = OffsetPage(items = listOf(card(3), card(1)), page = 4, hasNext = true, totalCount = 77)

        val spotPage = page.toGqlSpotPage()
        assertEquals(listOf(3L, 1L), spotPage.items.map { it.id })
        assertEquals(4, spotPage.page)
        assertTrue(spotPage.hasNext)
        assertEquals(77, spotPage.totalCount)

        val connection = page.toGqlSearchConnection()
        assertEquals(listOf(3L, 1L), connection.items.map { it.id })
        assertEquals(4, connection.page)
        assertTrue(connection.hasNext)
        assertEquals(77, connection.totalCount)
    }

    @Test
    fun `갤러리 이미지는 필드별로 옮기고 저작권 문구는 유형에서 나온다`() {
        val gql = SpotImage(
            imageUrl = "https://img/full.jpg",
            thumbnailUrl = "https://img/thumb.jpg",
            caption = "정문",
            copyright = CopyrightType.TYPE3,
        ).toGql()

        assertEquals("https://img/full.jpg", gql.imageUrl)
        assertEquals("https://img/thumb.jpg", gql.thumbnailUrl)
        assertEquals("정문", gql.caption)
        assertEquals(GqlCopyrightType.TYPE3, gql.copyright)
        assertEquals(CopyrightType.TYPE3.attributionText, gql.attributionText)
    }

    @Test
    fun `운영 정보는 일곱 필드가 제자리로 간다`() {
        val gql = SpotIntro(
            businessHours = "09~18",
            closedDays = "화",
            parking = "가능",
            inquiryPhone = "02-000",
            fee = "무료",
            petAllowed = "불가",
            creditCardAccepted = "가능(카드)",
        ).toGql()

        assertEquals("09~18", gql.businessHours)
        assertEquals("화", gql.closedDays)
        assertEquals("가능", gql.parking)
        assertEquals("02-000", gql.inquiryPhone)
        assertEquals("무료", gql.fee)
        assertEquals("불가", gql.petAllowed)
        assertEquals("가능(카드)", gql.creditCardAccepted)
    }

    @Test
    fun `이미지·운영 정보의 선택 필드가 모두 없어도 변환된다`() {
        val image = SpotImage("https://img/a.jpg", thumbnailUrl = null, caption = null, copyright = CopyrightType.UNSPECIFIED).toGql()
        assertNull(image.thumbnailUrl)
        assertNull(image.caption)

        val intro = SpotIntro(null, null, "주차장 있음", null, null, null, null).toGql()
        assertNull(intro.businessHours)
        assertNull(intro.closedDays)
        assertNull(intro.creditCardAccepted)
    }

    @Test
    fun `이용안내 항목은 이름과 내용이 뒤바뀌지 않는다`() {
        val gql = SpotInfoItem(name = "주차", text = "가능").toGql()

        assertEquals("주차", gql.name)
        assertEquals("가능", gql.text)
    }
}
