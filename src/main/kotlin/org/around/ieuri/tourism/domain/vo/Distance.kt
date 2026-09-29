package org.around.ieuri.tourism.domain.vo

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 두 좌표 사이의 직선거리(미터). Haversine 공식으로 계산한다(AC-2). */
@JvmInline
value class Distance private constructor(val meters: Double) : Comparable<Distance> {

    override fun compareTo(other: Distance): Int = meters.compareTo(other.meters)

    companion object {
        /** 지구 평균 반지름(미터). */
        private const val EARTH_RADIUS_METERS = 6_371_000.0

        fun ofMeters(meters: Double): Distance {
            require(meters >= 0.0) { "거리는 음수일 수 없습니다." }
            return Distance(meters)
        }

        /** 두 좌표 간 Haversine 직선거리. */
        fun between(from: Coordinates, to: Coordinates): Distance {
            val lat1 = Math.toRadians(from.latitude)
            val lat2 = Math.toRadians(to.latitude)
            val dLat = Math.toRadians(to.latitude - from.latitude)
            val dLon = Math.toRadians(to.longitude - from.longitude)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return Distance(EARTH_RADIUS_METERS * c)
        }
    }
}
