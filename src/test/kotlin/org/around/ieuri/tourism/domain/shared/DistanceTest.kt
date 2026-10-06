package org.around.ieuri.tourism.domain.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Distance — Haversine 직선거리(AC-2)와 Coordinates 유효성(AC-6). */
class DistanceTest {

    @Test
    fun `동일 좌표의 거리는 0`() {
        val p = Coordinates(37.5665, 126.9780) // 서울시청
        assertEquals(0.0, Distance.between(p, p).meters, 0.001)
    }

    @Test
    fun `서울시청과 강남역 거리는 실제값에 근접한다`() {
        val cityHall = Coordinates(37.5665, 126.9780)
        val gangnam = Coordinates(37.4979, 127.0276)
        // 실제 직선거리 약 8.6km. Haversine 오차 허용.
        val meters = Distance.between(cityHall, gangnam).meters
        assertTrue(meters in 8_000.0..9_500.0, "expected ~8.6km, got ${meters}m")
    }

    @Test
    fun `거리는 대칭이다`() {
        val a = Coordinates(35.1796, 129.0756) // 부산
        val b = Coordinates(33.4996, 126.5312) // 제주
        assertEquals(
            Distance.between(a, b).meters,
            Distance.between(b, a).meters,
            0.001,
        )
    }

    @Test
    fun `위도 범위를 벗어나면 거부한다`() {
        assertFailsWith<IllegalArgumentException> { Coordinates(91.0, 0.0) }
        assertFailsWith<IllegalArgumentException> { Coordinates(-90.1, 0.0) }
    }

    @Test
    fun `경도 범위를 벗어나면 거부한다`() {
        assertFailsWith<IllegalArgumentException> { Coordinates(0.0, 180.1) }
        assertFailsWith<IllegalArgumentException> { Coordinates(0.0, -181.0) }
    }

    @Test
    fun `좌표는 toString에 원값을 노출하지 않는다`() {
        val masked = Coordinates(37.5665, 126.9780).toString()
        assertTrue("37.5665" !in masked && "126.978" !in masked, "coords leaked: $masked")
    }
}
