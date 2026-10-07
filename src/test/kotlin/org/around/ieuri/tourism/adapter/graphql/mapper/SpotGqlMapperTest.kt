package org.around.ieuri.tourism.adapter.graphql.mapper

import org.around.ieuri.common.pagination.CursorEdge
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.CursorPageInfo
import org.around.ieuri.common.pagination.OffsetPage
import org.around.ieuri.tourism.application.catalog.provided.dto.FestivalView
import org.around.ieuri.tourism.application.catalog.provided.dto.LikedSpotView
import org.around.ieuri.tourism.application.catalog.provided.dto.PopularSpotView
import org.around.ieuri.tourism.application.catalog.provided.dto.SpotSuggestionView
import org.around.ieuri.tourism.application.catalog.provided.dto.SpotView
import org.around.ieuri.tourism.application.category.provided.dto.CategoryDisplay
import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.around.ieuri.tourism.domain.spot.SpotImage
import org.around.ieuri.tourism.domain.spot.SpotInfoItem
import org.around.ieuri.tourism.domain.spot.SpotIntro
import java.time.Instant
import java.time.LocalDate
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

    @Test
    fun `앱 구성 카테고리는 코드·라벨·아이콘을 옮긴다`() {
        val gql = CategoryDisplay(SpotCategory("RESTAURANT"), "음식점", "https://cdn/r.png").toGql()

        assertEquals("RESTAURANT", gql.code)
        assertEquals("음식점", gql.label)
        assertEquals("https://cdn/r.png", gql.iconUrl)
    }

    @Test
    fun `코드만 채운 카테고리는 라벨이 코드이고 아이콘이 없다`() {
        val gql = SpotCategory("SHOPPING").toGqlCodeOnly()

        assertEquals("SHOPPING", gql.code)
        assertEquals("SHOPPING", gql.label)
        assertNull(gql.iconUrl)
    }

    @Test
    fun `자동완성은 id·제목·카테고리 코드를 옮긴다`() {
        val gql = SpotSuggestionView(id = 5L, title = "경복궁", category = SpotCategory("TOURIST_SPOT")).toGql()

        assertEquals(5L, gql.id)
        assertEquals("경복궁", gql.title)
        assertEquals("TOURIST_SPOT", gql.category.code)
    }

    @Test
    fun `축제는 카드 필드와 기간을 필드마다 옮긴다`() {
        val gql = FestivalView(
            spot = SpotView(
                id = 31L, title = "빛 축제", address = "서울 중구", thumbnailImageUrl = "https://img/31.jpg", distanceMeters = 410.5,
                category = SpotCategory("FESTIVAL_EVENT"), copyright = CopyrightType.TYPE3, mapX = 126.98, mapY = 37.57,
            ),
            eventStartDate = LocalDate.of(2026, 8, 1),
            eventEndDate = LocalDate.of(2026, 8, 10),
        ).toGql()

        assertEquals(31L, gql.id)
        assertEquals("빛 축제", gql.title)
        assertEquals("서울 중구", gql.address)
        assertEquals("https://img/31.jpg", gql.thumbnailImageUrl)
        assertEquals(410.5, gql.distanceMeters)
        assertEquals("FESTIVAL_EVENT", gql.category.code)
        assertEquals(GqlCopyrightType.TYPE3, gql.copyright)
        assertEquals(CopyrightType.TYPE3.attributionText, gql.attributionText)
        assertEquals(126.98, gql.mapX)
        assertEquals(37.57, gql.mapY)
        assertEquals("2026-08-01", gql.eventStartDate)
        assertEquals("2026-08-10", gql.eventEndDate)
    }

    @Test
    fun `좋아요 커넥션은 노드와 엣지의 likedAt·경계를 옮긴다`() {
        val liked = LikedSpotView(
            id = 41L,
            title = "광장시장",
            thumbnailImageUrl = "https://img/41.jpg",
            address = null,
            category = SpotCategory("SHOPPING"),
            copyright = CopyrightType.TYPE1,
            likedAt = Instant.parse("2026-08-02T03:04:05Z"),
            catalogId = 410L,
        )
        val page = CursorPage(
            edges = listOf(CursorEdge("c-41", liked)),
            pageInfo = CursorPageInfo(hasNextPage = false, hasPreviousPage = true, startCursor = "c-41", endCursor = "c-41"),
            totalCount = null,
        )

        val gql = page.toGqlLikedSpotConnection()

        val edge = gql.edges.single()
        assertEquals("c-41", edge.cursor)
        assertEquals("2026-08-02T03:04:05Z", edge.likedAt)
        assertEquals(41L, edge.node.id)
        assertEquals("광장시장", edge.node.title)
        assertEquals("https://img/41.jpg", edge.node.thumbnailImageUrl)
        assertNull(edge.node.address)
        assertEquals("SHOPPING", edge.node.category.code)
        assertEquals(GqlCopyrightType.TYPE1, edge.node.copyright)
        assertEquals(CopyrightType.TYPE1.attributionText, edge.node.attributionText)
        assertEquals(false, gql.pageInfo.hasNextPage)
        assertEquals(true, gql.pageInfo.hasPreviousPage)
    }

    @Test
    fun `인기 커넥션은 좋아요 수를 엣지에 싣는다`() {
        val page = CursorPage(
            edges = listOf(CursorEdge("c-7", PopularSpotView(spot = card(7), likeCount = 33L))),
            pageInfo = CursorPageInfo(hasNextPage = true, hasPreviousPage = false, startCursor = "c-7", endCursor = "c-7"),
            totalCount = null,
        )

        val gql = page.toGqlPopularSpotConnection()

        assertEquals(33, gql.edges.single().likeCount)
        assertEquals(7L, gql.edges.single().node.id)
        assertEquals(true, gql.pageInfo.hasNextPage)
    }
}
