package org.around.ieuri.tourism.domain.specification

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 좌표가 가까운 것끼리 묶는 규칙(SPEC-0029). 한 카탈로그에 접힌 원본들이 실제로 몇 장소인지
 * 가른다.
 *
 * **정확 일치로 묶으면 안 된다** — 같은 장소인데 언어별 좌표가 미세하게 다른 경우가 실재한다
 * (`솔로몬로파크`의 KO와 외국어 행은 1.6m 차이). 그래서 [RADIUS_METERS] 안이면 한 군집이다.
 *
 * 연결 성분(transitive)으로 묶는다 — A-B가 가깝고 B-C가 가까우면 A·B·C가 한 군집이다.
 * 실측 데이터의 군집은 지름이 미터 단위고 군집 간 거리는 100km대라, 사슬처럼 이어져
 * 무관한 장소가 합쳐질 여지가 없다.
 */
object OriginProximityClustering {

    /** 이 거리 안의 두 원본은 같은 장소로 본다. */
    const val RADIUS_METERS = 1_000.0

    /**
     * [items]를 좌표 근접도로 묶는다. **입력 순서를 보존한다** — 군집은 첫 원소가 나온 순서로,
     * 군집 안도 입력 순서로 정렬된다. 호출자가 "가장 큰 군집"을 고를 때 동수면 앞선 것을 잡게
     * 되므로 순서가 결과를 정한다.
     */
    fun <T> cluster(items: List<T>, coordinateOf: (T) -> Pair<Double, Double>): List<List<T>> {
        // 군집 대표의 좌표를 들고 순차적으로 붙인다. 원본 수가 한 카탈로그당 한 자릿수라
        // O(n²)로 충분하고, 공간 인덱스를 세우는 편이 오히려 복잡하다.
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
                    // 첫 군집에 합치고 나머지를 흡수한다 — 순서를 지키려면 앞선 군집이 남아야 한다.
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
     * 두 좌표 사이의 거리(미터). Haversine이며 입력은 **경도, 위도 순**이다 — 뒤집으면
     * 조용히 틀린 거리가 나온다.
     *
     * 지구를 구로 보는 근사라 WGS84 타원체 기준([org.around.ieuri.tourism.application.port.out.query.SpotCatalogSpatialQueryPort]의
     * PostGIS `ST_Distance`) 대비 0.5%까지 어긋난다. 1km 판정에서 5m 오차이고, 실측 군집이
     * 미터 단위 대 100km 단위로 갈려 있어 경계에 걸리는 데이터가 없다.
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
