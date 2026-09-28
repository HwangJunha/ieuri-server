package org.around.ieuri.tourism.domain.specification

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 한 카탈로그에 접힌 원본들을 좌표 근접도로 묶는다(SPEC-0029).
 * - [RADIUS_METERS] 안이면 같은 장소이며, 연결 성분으로 묶는다(A-B, B-C가 가까우면 A·B·C가 한 군집).
 */
object OriginProximityClustering {

    /** 이 거리 안의 두 원본은 같은 장소로 본다. */
    const val RADIUS_METERS = 1_000.0

    /** [items]를 좌표 근접도로 묶는다. 군집 순서와 군집 안 순서 모두 입력 순서를 보존한다. */
    fun <T> cluster(items: List<T>, coordinateOf: (T) -> Pair<Double, Double>): List<List<T>> {
        val clusters = mutableListOf<MutableList<T>>()
        items.forEach { item ->
            val (lon, lat) = coordinateOf(item)
            // 이 원소가 닿는 군집 전부 — 둘 이상이면 그 군집들이 이 원소를 통해 이어진다.
            val touching = clusters.filter { cluster ->
                cluster.any { member ->
                    val (memberLon, memberLat) = coordinateOf(member)
                    distanceMeters(lon, lat, memberLon, memberLat) <= RADIUS_METERS
                }
            }
            when {
                touching.isEmpty() -> clusters += mutableListOf(item)

                else -> {
                    // 첫 군집에 합치고 나머지를 흡수한다.
                    val target = touching.first()
                    touching.drop(1).forEach { merged ->
                        target += merged
                        clusters.remove(merged)
                    }
                    target += item
                }
            }
        }
        return clusters.map { it.toList() }
    }

    /**
     * 두 좌표 사이의 Haversine 거리(미터). 입력은 경도, 위도 순이다.
     * - 구 근사라 PostGIS `ST_Distance`(WGS84) 대비 0.5%까지 어긋난다.
     */
    fun distanceMeters(lon1: Double, lat1: Double, lon2: Double, lat2: Double): Double {
        if (abs(lon1 - lon2) < EPSILON && abs(lat1 - lat2) < EPSILON) return 0.0
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaPhi = Math.toRadians(lat2 - lat1)
        val deltaLambda = Math.toRadians(lon2 - lon1)
        val a = sin(deltaPhi / 2) * sin(deltaPhi / 2) +
            cos(phi1) * cos(phi2) * sin(deltaLambda / 2) * sin(deltaLambda / 2)
        return EARTH_RADIUS_METERS * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /** 평균 지구 반지름(IUGG). */
    private const val EARTH_RADIUS_METERS = 6_371_008.8

    private const val EPSILON = 1e-12
}
