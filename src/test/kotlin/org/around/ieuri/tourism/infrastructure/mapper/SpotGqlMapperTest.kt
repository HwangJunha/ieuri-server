package org.around.ieuri.tourism.infrastructure.mapper

import org.around.ieuri.tourism.application.query.SpotView
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql.mapper.toGql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
            attributionText = "공공누리 제1유형",
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
        assertEquals("공공누리 제1유형", gql.attributionText)
        assertEquals(126.977, gql.mapX)
        assertEquals(37.579, gql.mapY)
    }

    @Test
    fun `주소가 없으면 null이다`() {
        val gql = SpotView(
            id = 1L, title = "t", address = null, thumbnailImageUrl = "u", distanceMeters = 0.0,
            category = SpotCategory.TOURIST_SPOT, copyright = CopyrightType.UNSPECIFIED, attributionText = "a",
            mapX = 0.0, mapY = 0.0,
        ).toGql()
        assertNull(gql.address)
    }
}
