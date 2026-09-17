package org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql.mapper

import org.around.ieuri.tourism.application.query.MapSpotView
import org.around.ieuri.tourism.domain.vo.SpotCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.around.ieuri.common.graphql.generated.types.MapBoundsInput as GqlMapBoundsInput

/**
 * 지도 응답·입력 변환(POL-033). **필드마다 값을 다르게 둔다** — 같은 값을 공유하면 두 필드가
 * 뒤바뀌어도 통과한다. 좌표는 특히 위경도가 같은 Double이라 스왑이 컴파일로 잡히지 않는다.
 */
class MapGqlMapperTest {

    @Test
    fun `MapSpotView는 필드마다 제자리로 옮겨진다`() {
        val view = MapSpotView(
            id = 4242L,
            title = "명동 교자",
            thumbnailImageUrl = "https://img.example/mdk.jpg",
            category = SpotCategory("RESTAURANT"),
            // 경도와 위도를 서로 다른 값으로 둔다 — 뒤바뀌면 아래 두 단언이 함께 깨진다.
            lng = 126.9851,
            lat = 37.5636,
        )

        val gql = view.toGql()

        assertEquals(4242L, gql.id)
        assertEquals("명동 교자", gql.title)
        assertEquals("https://img.example/mdk.jpg", gql.thumbnailImageUrl)
        assertEquals("RESTAURANT", gql.category.code)
        assertEquals(126.9851, gql.lng)
        assertEquals(37.5636, gql.lat)
    }

    @Test
    fun `마커는 카테고리 라벨을 코드로 채우고 아이콘을 비운다`() {
        val gql = MapSpotView(
            id = 1L,
            title = "t",
            thumbnailImageUrl = "u",
            category = SpotCategory("SHOPPING"),
            lng = 1.0,
            lat = 2.0,
        ).toGql()

        // 표시 문구는 availableSpotCategories가 담당한다 — 마커는 코드만 쓴다.
        assertEquals("SHOPPING", gql.category.label)
        assertNull(gql.category.iconUrl)
    }

    @Test
    fun `bbox 입력은 네 변이 제자리로 옮겨진다`() {
        // 네 값을 전부 다르게 둔다 — min/max나 위경도가 뒤바뀌면 깨진다.
        val bounds = GqlMapBoundsInput(minLat = 37.41, minLng = 126.82, maxLat = 37.63, maxLng = 127.14).toDomain()

        requireNotNull(bounds)
        assertEquals(37.41, bounds.minLat)
        assertEquals(126.82, bounds.minLng)
        assertEquals(37.63, bounds.maxLat)
        assertEquals(127.14, bounds.maxLng)
    }

    @Test
    fun `뒤집히거나 범위 밖인 bbox는 null이다`() {
        // 오류 코드 선택은 리졸버 몫이라 mapper는 null만 돌려준다.
        assertNull(GqlMapBoundsInput(minLat = 37.6, minLng = 126.8, maxLat = 37.4, maxLng = 127.1).toDomain())
        assertNull(GqlMapBoundsInput(minLat = 37.4, minLng = 127.1, maxLat = 37.6, maxLng = 126.8).toDomain())
        assertNull(GqlMapBoundsInput(minLat = -91.0, minLng = 126.8, maxLat = 37.6, maxLng = 127.1).toDomain())
        assertNull(GqlMapBoundsInput(minLat = 37.4, minLng = 126.8, maxLat = 91.0, maxLng = 127.1).toDomain())
    }
}
