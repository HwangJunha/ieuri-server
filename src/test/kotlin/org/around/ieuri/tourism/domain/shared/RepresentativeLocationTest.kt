package org.around.ieuri.tourism.domain.shared

import org.around.ieuri.tourism.domain.shared.RepresentativeLocation.Point
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/** 시군구 대표 위치(SPEC-0015) — 가장 밀집한 격자에서 무게중심에 가장 가까운 실제 원본. */
class RepresentativeLocationTest {

    private val cell = 0.01

    @Test
    @DisplayName("가장 밀집한 격자의 무게중심에 가장 가까운 원본을 고른다")
    fun `picks the origin nearest to the densest cell centroid`() {
        val points = listOf(
            // 격자 (127.00, 37.00): 셋. 무게중심 (127.0010, 37.0010) — 2번이 가장 가깝다.
            Point(1, 127.0000, 37.0000),
            Point(2, 127.0011, 37.0009),
            Point(3, 127.0020, 37.0020),
            // 격자 (127.05, 37.05): 하나.
            Point(4, 127.0500, 37.0500),
        )

        assertEquals(RepresentativeLocation.Choice(spotId = 2, spotCount = 3), RepresentativeLocation.choose(points, cell, minSpotCount = 2))
    }

    @Test
    @DisplayName("가장 밀집한 격자도 문턱에 못 미치면 고르지 않는다")
    fun `nothing is chosen below the threshold`() {
        assertNull(RepresentativeLocation.choose(listOf(Point(1, 127.0, 37.0), Point(2, 127.5, 37.5)), cell, minSpotCount = 2))
    }

    @Test
    @DisplayName("격자 원본 수가 같으면 경도가 작은 격자를 고른다")
    fun `ties go to the cell with the smaller longitude`() {
        // 1도 격자: 130과 127 두 격자가 둘씩. 127 격자의 두 원본은 무게중심(127.0)까지 0.25로 같다(2진 소수라 정확하다).
        val points = listOf(Point(1, 130.0, 37.0), Point(2, 130.25, 37.0), Point(4, 127.25, 37.0), Point(3, 126.75, 37.0))

        assertEquals(RepresentativeLocation.Choice(spotId = 3, spotCount = 2), RepresentativeLocation.choose(points, 1.0, minSpotCount = 2))
    }

    @Test
    @DisplayName("무게중심까지 거리가 같으면 원본 id가 작은 쪽이다")
    fun `equal distances go to the smaller spot id`() {
        val points = listOf(Point(9, 126.75, 37.0), Point(5, 127.25, 37.0))

        assertEquals(5L, RepresentativeLocation.choose(points, 1.0, minSpotCount = 2)?.spotId)
    }
}
