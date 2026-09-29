package org.around.ieuri.tourism.domain

import org.around.ieuri.tourism.domain.vo.MapBounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** 뷰포트 bbox의 유효성과 면적(SPEC-0014 R4·R5). */
class MapBoundsTest {

    @Test
    fun `정상 bbox는 만들어진다`() {
        val bounds = MapBounds.ofOrNull(minLat = 37.4, minLng = 126.9, maxLat = 37.6, maxLng = 127.1)

        assertNotNull(bounds)
        assertEquals(37.4, bounds.minLat)
        assertEquals(126.9, bounds.minLng)
        assertEquals(37.6, bounds.maxLat)
        assertEquals(127.1, bounds.maxLng)
    }

    @Test
    fun `위도가 뒤집힌 bbox는 거절된다`() {
        // AC R4. minLat >= maxLat이면 INVALID_BBOX다.
        assertNull(MapBounds.ofOrNull(minLat = 37.6, minLng = 126.9, maxLat = 37.4, maxLng = 127.1))
    }

    @Test
    fun `위도가 같으면 거절된다`() {
        // 넓이 0은 "뒤집힘"과 같이 다룬다.
        assertNull(MapBounds.ofOrNull(minLat = 37.5, minLng = 126.9, maxLat = 37.5, maxLng = 127.1))
    }

    @Test
    fun `경도가 뒤집힌 bbox는 거절된다`() {
        // 날짜변경선을 넘는 bbox(minLng > maxLng)는 지원하지 않는다.
        assertNull(MapBounds.ofOrNull(minLat = 37.4, minLng = 127.1, maxLat = 37.6, maxLng = 126.9))
    }

    @Test
    fun `위도 90을 넘으면 거절된다`() {
        // AC R4.
        assertNull(MapBounds.ofOrNull(minLat = 37.4, minLng = 126.9, maxLat = 90.1, maxLng = 127.1))
    }

    @Test
    fun `위도 마이너스 90 미만이면 거절된다`() {
        assertNull(MapBounds.ofOrNull(minLat = -90.1, minLng = 126.9, maxLat = 37.6, maxLng = 127.1))
    }

    @Test
    fun `경도가 범위를 벗어나면 거절된다`() {
        assertNull(MapBounds.ofOrNull(minLat = 37.4, minLng = -180.1, maxLat = 37.6, maxLng = 127.1))
        assertNull(MapBounds.ofOrNull(minLat = 37.4, minLng = 126.9, maxLat = 37.6, maxLng = 180.1))
    }

    @Test
    fun `극단값 자체는 유효하다`() {
        // 90·180은 범위 밖이 아니다(경계 포함).
        assertNotNull(MapBounds.ofOrNull(minLat = -90.0, minLng = -180.0, maxLat = 90.0, maxLng = 180.0))
    }

    @Test
    fun `NaN이나 무한대는 거절된다`() {
        assertNull(MapBounds.ofOrNull(minLat = Double.NaN, minLng = 126.9, maxLat = 37.6, maxLng = 127.1))
        assertNull(
            MapBounds.ofOrNull(
                minLat = 37.4,
                minLng = Double.NEGATIVE_INFINITY,
                maxLat = 37.6,
                maxLng = 127.1,
            ),
        )
    }

    @Test
    fun `면적은 위경도 도 곱이다`() {
        // AC R5의 상한 비교 기준. 제곱미터가 아니라 도 곱이다.
        val bounds = MapBounds.ofOrNull(minLat = 37.0, minLng = 126.0, maxLat = 37.5, maxLng = 127.0)

        assertNotNull(bounds)
        assertEquals(0.5, bounds.areaInSquareDegrees, 1e-9)
    }

    @Test
    fun `중심점은 위경도 평균이다`() {
        // 네 변을 전부 다른 값으로 둬 위경도가 뒤바뀌면 깨지게 한다.
        val bounds = MapBounds.ofOrNull(minLat = 37.0, minLng = 126.0, maxLat = 37.5, maxLng = 127.0)

        assertNotNull(bounds)
        assertEquals(37.25, bounds.center.latitude, 1e-9)
        assertEquals(126.5, bounds.center.longitude, 1e-9)
    }

    @Test
    fun `적도와 본초자오선을 걸치는 영역의 중심점도 평균이다`() {
        // 음수 좌표에서 부호를 잃지 않는지.
        val bounds = MapBounds.ofOrNull(minLat = -1.0, minLng = -2.0, maxLat = 3.0, maxLng = 6.0)

        assertNotNull(bounds)
        assertEquals(1.0, bounds.center.latitude, 1e-9)
        assertEquals(2.0, bounds.center.longitude, 1e-9)
    }
}
