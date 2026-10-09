package org.around.ieuri.tourism.domain.region.policy

import org.around.ieuri.tourism.domain.region.policy.RepresentativeLocation.Point
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/** 시군구 대표 위치(SPEC-0015) — 가장 밀집한 격자에서 무게중심에 가장 가까운 실제 원본. 격자 순위는 DB가 준다. */
class RepresentativeLocationTest {

    @Test
    @DisplayName("가장 밀집한 격자의 무게중심에 가장 가까운 원본을 고른다")
    fun `picks the origin nearest to the densest cell centroid`() {
        val points = listOf(
            // 격자 1: 셋. 무게중심 (127.0010, 37.0010) — 2번이 가장 가깝다.
            Point(1, 127.0000, 37.0000, cellRank = 1),
            Point(2, 127.0011, 37.0009, cellRank = 1),
            Point(3, 127.0020, 37.0020, cellRank = 1),
            // 격자 2: 하나.
            Point(4, 127.0500, 37.0500, cellRank = 2),
        )

        assertEquals(RepresentativeLocation.Choice(spotId = 2, spotCount = 3), RepresentativeLocation.choose(points, minSpotCount = 2))
    }

    @Test
    @DisplayName("가장 밀집한 격자도 문턱에 못 미치면 고르지 않는다")
    fun `nothing is chosen below the threshold`() {
        assertNull(RepresentativeLocation.choose(listOf(Point(1, 127.0, 37.0, 1), Point(2, 127.5, 37.5, 2)), minSpotCount = 2))
    }

    @Test
    @DisplayName("격자 원본 수가 같으면 DB 순위가 앞선 격자를 고른다 - 좌표 순서가 아니다")
    fun `ties go to the cell ranked first by the database`() {
        // 순위 1 격자가 경도는 더 크다. 좌표로 고르면 순위 2 격자가 된다.
        val points = listOf(Point(1, 130.0, 37.0, 1), Point(2, 130.25, 37.0, 1), Point(4, 127.25, 37.0, 2), Point(3, 126.75, 37.0, 2))

        assertEquals(RepresentativeLocation.Choice(spotId = 1, spotCount = 2), RepresentativeLocation.choose(points, minSpotCount = 2))
    }

    @Test
    @DisplayName("무게중심까지 거리가 같으면 원본 id가 작은 쪽이다")
    fun `equal distances go to the smaller spot id`() {
        val points = listOf(Point(9, 126.75, 37.0, 1), Point(5, 127.25, 37.0, 1))

        assertEquals(5L, RepresentativeLocation.choose(points, minSpotCount = 2)?.spotId)
    }
}
