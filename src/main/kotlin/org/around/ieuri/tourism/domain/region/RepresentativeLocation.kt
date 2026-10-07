package org.around.ieuri.tourism.domain.region

import kotlin.math.round

/**
 * 시군구 대표 위치 선정(SPEC-0015). 원본이 가장 밀집한 격자를 찾아, 그 격자의 무게중심에 가장 가까운 **실제 원본**을 고른다.
 * - 격자 중심 자체를 쓰지 않는다 — 시군구 경계 밖으로 나갈 수 있다.
 * - 격자는 PostGIS `ST_SnapToGrid`와 같다(좌표를 격자 크기의 가장 가까운 배수로, 반올림은 짝수 쪽). 단위는 도다.
 * - 동수면 격자 좌표(경도, 위도)가 작은 쪽, 거리가 같으면 원본 id가 작은 쪽이다.
 */
object RepresentativeLocation {

    data class Point(val spotId: Long, val longitude: Double, val latitude: Double)

    data class Choice(val spotId: Long, val spotCount: Int)

    /** 가장 밀집한 격자의 원본 수가 [minSpotCount]에 못 미치면 null이다. */
    fun choose(points: List<Point>, cellSizeDegrees: Double, minSpotCount: Int): Choice? {
        require(cellSizeDegrees > 0) { "격자 크기는 양수여야 합니다." }
        val densest = points.groupBy { snap(it.longitude, cellSizeDegrees) to snap(it.latitude, cellSizeDegrees) }
            .filterValues { it.size >= minSpotCount }
            .entries
            .minWithOrNull(compareByDescending<Map.Entry<Pair<Double, Double>, List<Point>>> { it.value.size }.thenBy { it.key.first }.thenBy { it.key.second })
            ?.value
            ?: return null

        val centerX = densest.map { it.longitude }.average()
        val centerY = densest.map { it.latitude }.average()
        val nearest = densest.minWith(compareBy<Point> { (it.longitude - centerX).squared() + (it.latitude - centerY).squared() }.thenBy { it.spotId })
        return Choice(spotId = nearest.spotId, spotCount = densest.size)
    }

    private fun snap(value: Double, size: Double): Double = round(value / size) * size

    private fun Double.squared(): Double = this * this
}
