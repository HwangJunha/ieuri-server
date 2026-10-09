package org.around.ieuri.tourism.domain.region

/**
 * 시군구 대표 위치 선정(SPEC-0015). 원본이 가장 밀집한 격자를 찾아, 그 격자의 무게중심에 가장 가까운 **실제 원본**을 고른다.
 * - 격자 중심 자체를 쓰지 않는다 — 시군구 경계 밖으로 나갈 수 있다.
 * - 격자와 그 순서는 [Point.cellRank]로 받는다(PostGIS `ST_SnapToGrid`·geometry 정렬). 원본 수가 같으면 순위가 앞선 격자다.
 * - 무게중심까지 거리가 같으면 원본 id가 작은 쪽이다.
 */
object RepresentativeLocation {

    /** [cellRank]는 시군구 안에서 격자의 순위다 — 같은 값이면 같은 격자다. */
    data class Point(val spotId: Long, val longitude: Double, val latitude: Double, val cellRank: Long)

    data class Choice(val spotId: Long, val spotCount: Int)

    /** 가장 밀집한 격자의 원본 수가 [minSpotCount]에 못 미치면 null이다. */
    fun choose(points: List<Point>, minSpotCount: Int): Choice? {
        val densest = points.groupBy { it.cellRank }
            .filterValues { it.size >= minSpotCount }
            .entries
            .minWithOrNull(compareByDescending<Map.Entry<Long, List<Point>>> { it.value.size }.thenBy { it.key })
            ?.value
            ?: return null

        val centerX = densest.map { it.longitude }.average()
        val centerY = densest.map { it.latitude }.average()
        val nearest = densest.minWith(compareBy<Point> { (it.longitude - centerX).squared() + (it.latitude - centerY).squared() }.thenBy { it.spotId })
        return Choice(spotId = nearest.spotId, spotCount = densest.size)
    }

    private fun Double.squared(): Double = this * this
}
