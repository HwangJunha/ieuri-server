package org.around.ieuri.tourism.adapter.graphql.mapper

import org.around.ieuri.tourism.application.catalog.provided.exception.InvalidCoordinatesException
import org.around.ieuri.tourism.application.region.provided.dto.RecommendedRegionItemView
import org.around.ieuri.tourism.application.region.provided.dto.RecommendedRegionView
import org.around.ieuri.tourism.application.region.provided.dto.RegionScoreView
import org.around.ieuri.tourism.domain.region.RegionScore
import org.around.ieuri.tourism.domain.region.RegionScoreFactor
import org.around.ieuri.tourism.domain.shared.Coordinates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.around.ieuri.common.graphql.generated.types.CoordinatesInput as GqlCoordinatesInput

/** 지역 추천 입력·응답 변환. 위도·경도와 점수·순위는 값을 다르게 둔다. */
class RegionGqlMapperTest {

    @Test
    fun `좌표 입력은 lat을 위도로, lng을 경도로 옮긴다`() {
        val coordinates = GqlCoordinatesInput(lat = 37.5, lng = 127.03).toDomain()

        assertEquals(37.5, coordinates.latitude)
        assertEquals(127.03, coordinates.longitude)
    }

    @Test
    fun `범위를 벗어난 좌표는 INVALID_COORDINATES이고 메시지에 좌표가 없다`() {
        val e = assertFailsWith<InvalidCoordinatesException> { GqlCoordinatesInput(lat = 91.0, lng = 127.0).toDomain() }
        assertEquals(false, e.message.orEmpty().contains("91"))
    }

    @Test
    fun `추천 시군구는 필드마다 제자리로 옮겨진다`() {
        val view = RecommendedRegionView(
            regions = listOf(
                RecommendedRegionItemView(
                    code = "11680",
                    name = "강남구",
                    provinceName = "서울특별시",
                    scores = listOf(RegionScoreView(RegionScore(RegionScoreFactor.STAY_INTENSITY, 72.5), rank = 3)),
                    scoreFactorCount = 4,
                    center = Coordinates(latitude = 37.49, longitude = 127.06),
                ),
            ),
        )

        val item = view.toGql().regions.single()

        assertEquals("11680", item.code)
        assertEquals("강남구", item.name)
        assertEquals("서울특별시", item.provinceName)
        assertEquals(4, item.scoreFactorCount)
        assertEquals(37.49, item.center.lat)
        assertEquals(127.06, item.center.lng)
        val score = item.scores.single()
        assertEquals("STAY_INTENSITY", score.factor.name)
        assertEquals(72.5, score.value)
        assertEquals(3, score.rank)
    }
}
