package org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql.mapper

import org.around.ieuri.tourism.application.query.MapSpotView
import org.around.ieuri.tourism.domain.vo.SpotCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 지도 응답 변환(POL-033). **필드마다 값을 다르게 둔다** — 같은 값을 공유하면 두 필드가
 * 뒤바뀌어도 통과한다. 좌표는 특히 위경도가 같은 Double이라 스왑이 컴파일로 잡히지 않는다.
 *
 * 입력 변환은 여기 없다 — bbox는 원시 좌표로 응용 계층에 넘어가고 도메인 VO 조립은 `boundsOf`가
 * 하므로, 그 검증은 `MapRecommendationServiceTest`가 덮는다.
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

}
