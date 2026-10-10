package org.around.ieuri.tourism.domain.catalog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 좌표 근접 군집(SPEC-0029). 한 카탈로그에 접힌 원본이 실제로 몇 장소인지 가른다.
 */
class OriginProximityClusteringTest {

    private val incheon = 126.4158814558 to 37.4393229658
    private val gwangju = 126.9500493009 to 35.2538411163

    private fun cluster(vararg points: Pair<Double, Double>) = OriginProximityClustering.cluster(points.toList()) { it }

    @Test
    @DisplayName("1km 밖의 점은 다른 군집이다")
    fun `distant points form separate clusters`() {
        val clusters = cluster(incheon, gwangju)

        assertEquals(2, clusters.size)
        assertEquals(listOf(incheon), clusters[0])
        assertEquals(listOf(gwangju), clusters[1])
    }

    @Test
    @DisplayName("미터 단위로 다른 좌표는 같은 장소다")
    fun `metre-scale differences stay in one cluster`() {
        // 언어별 좌표가 1.6m 다른 같은 장소는 한 군집이다.
        val koRow = 126.93131322530203 to 35.18953087389348
        val foreignRow = 126.9313275748 to 35.189570284

        val clusters = cluster(koRow, foreignRow)

        assertEquals(1, clusters.size)
        assertEquals(listOf(koRow, foreignRow), clusters.single())
    }

    @Test
    @DisplayName("A-B와 B-C가 가까우면 A·B·C가 한 군집이다")
    fun `proximity is transitive`() {
        // 연결 성분으로 묶는다 — 세 점이 사슬처럼 이어지면 A와 C가 1km를 넘어도 한 장소다.
        val a = 126.0 to 37.0
        val b = 126.008 to 37.0
        val c = 126.016 to 37.0

        val clusters = cluster(a, c, b)

        assertEquals(1, clusters.size, "b가 a와 c를 잇는다")
        // 입력 순서를 지킨다 — 흡수된 군집의 원소가 뒤에 붙는다.
        assertEquals(listOf(a, c, b), clusters.single())
    }

    @Test
    @DisplayName("군집은 첫 원소가 나온 입력 순서를 지킨다")
    fun `clusters preserve input order`() {
        val clusters = cluster(gwangju, incheon)

        assertEquals(listOf(gwangju), clusters[0])
        assertEquals(listOf(incheon), clusters[1])
    }

    @Test
    @DisplayName("빈 입력은 빈 결과다")
    fun `empty input yields no clusters`() {
        assertTrue(OriginProximityClustering.cluster(emptyList<Pair<Double, Double>>()) { it }.isEmpty())
    }

    @Test
    @DisplayName("점 하나는 군집 하나다")
    fun `single point is one cluster`() {
        assertEquals(listOf(listOf(incheon)), cluster(incheon))
    }

    @Test
    @DisplayName("같은 좌표는 거리가 0이다")
    fun `identical coordinates measure zero`() {
        val (lon, lat) = incheon
        assertEquals(0.0, OriginProximityClustering.distanceMeters(lon, lat, lon, lat))
    }

    @Test
    @DisplayName("거리는 경도·위도 순으로 받는다")
    fun `distance takes longitude first`() {
        // 인천과 광주는 약 247km다.
        val (incheonLon, incheonLat) = incheon
        val (gwangjuLon, gwangjuLat) = gwangju

        val correct = OriginProximityClustering.distanceMeters(incheonLon, incheonLat, gwangjuLon, gwangjuLat)
        val swapped = OriginProximityClustering.distanceMeters(incheonLat, incheonLon, gwangjuLat, gwangjuLon)

        // PostGIS ST_Distance 실측값 247,206m 대비 Haversine 근사는 0.5% 안이다.
        assertTrue(correct in 246_000.0..249_000.0, "실측 247km와 맞아야 한다: $correct")
        assertTrue(swapped !in 246_000.0..249_000.0, "뒤집으면 다른 값이 나온다: $swapped")
    }

    @Test
    @DisplayName("1km 판정은 경계에서 갈린다")
    fun `the radius boundary separates clusters`() {
        // 위도 37°에서 경도 0.01°는 약 884m, 0.02°는 약 1,768m다.
        val base = 126.0 to 37.0
        val within = 126.01 to 37.0
        val beyond = 126.02 to 37.0

        assertEquals(1, cluster(base, within).size)
        assertEquals(2, cluster(base, beyond).size)
    }
}
