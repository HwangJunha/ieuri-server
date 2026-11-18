package org.around.ieuri.tourism.adapter.graphql.mapper

import org.around.ieuri.tourism.application.catalog.provided.dto.MapViewportView
import org.around.ieuri.tourism.application.catalog.provided.exception.InvalidBboxException
import org.around.ieuri.tourism.application.congestion.provided.dto.CongestionAreaBoundaryView
import org.around.ieuri.tourism.application.congestion.provided.dto.CongestionAreaView
import org.around.ieuri.tourism.application.congestion.provided.dto.CulturalEventView
import org.around.ieuri.tourism.application.congestion.provided.dto.SeoulCongestionView
import org.around.ieuri.tourism.domain.congestion.CommercialActivityLevel
import org.around.ieuri.tourism.domain.congestion.CommercialStatus
import org.around.ieuri.tourism.domain.congestion.CongestionForecast
import org.around.ieuri.tourism.domain.congestion.CongestionLevel
import org.around.ieuri.tourism.domain.congestion.CongestionSnapshot
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.around.ieuri.common.graphql.generated.types.MapBoundsInput as GqlMapBoundsInput

/** 지도·혼잡도 응답 변환. 같은 타입의 필드(인구 하한·상한, 경도·위도)는 값을 다르게 둔다. */
class CongestionGqlMapperTest {

    private val observed = LocalDateTime.of(2026, 8, 10, 14, 5)

    @Test
    fun `문화행사는 필드마다 제자리로 옮겨진다`() {
        val gql = CulturalEventView("e1", "빛 축제", "광장", "2026-08-01~2026-08-10", 127.01, 37.56, "https://e.example").toGql()

        assertEquals("e1", gql.id)
        assertEquals("빛 축제", gql.title)
        assertEquals("광장", gql.place)
        assertEquals("2026-08-01~2026-08-10", gql.period)
        assertEquals(127.01, gql.lng)
        assertEquals(37.56, gql.lat)
        assertEquals("https://e.example", gql.url)
    }

    @Test
    fun `문화행사 링크가 없으면 null이다`() {
        assertNull(CulturalEventView("e1", "t", "p", "기간", 1.0, 2.0, null).toGql().url)
    }

    @Test
    fun `뷰포트는 마커·행사·잘림을 옮긴다`() {
        val gql = MapViewportView(
            spots = emptyList(),
            events = listOf(CulturalEventView("e1", "t", "p", "기간", 1.0, 2.0, null)),
            truncated = true,
        ).toGql()

        assertEquals(listOf("e1"), gql.events.map { it.id })
        assertTrue(gql.spots.isEmpty())
        assertTrue(gql.truncated)
    }

    @Test
    fun `혼잡도 구역은 현재값·예보·상권을 필드마다 옮긴다`() {
        val area = CongestionAreaView(
            id = "POI009",
            name = "광화문",
            congestion = CongestionSnapshot(CongestionLevel.CROWDED, populationMin = 3000, populationMax = 3500, observedAt = observed),
            forecast = listOf(CongestionForecast(observed.plusHours(1), CongestionLevel.NORMAL, populationMin = 2000, populationMax = 2600)),
            commercial = CommercialStatus(CommercialActivityLevel.QUIET, isDefaulted = true, observedAt = observed.minusMinutes(5)),
        )
        val gql = SeoulCongestionView(areas = listOf(area), degraded = true, staleSince = observed.minusHours(2)).toGql()

        assertTrue(gql.degraded)
        assertEquals(observed.minusHours(2).toString(), gql.staleSince)
        val a = gql.areas.single()
        assertEquals("POI009", a.id)
        assertEquals("광화문", a.name)
        assertEquals("CROWDED", a.congestion.level.name)
        assertEquals(3000, a.congestion.populationMin)
        assertEquals(3500, a.congestion.populationMax)
        assertEquals(observed.toString(), a.congestion.observedAt)
        val f = a.forecast!!.single()
        assertEquals(observed.plusHours(1).toString(), f.forecastTime)
        assertEquals("NORMAL", f.level.name)
        assertEquals(2000, f.populationMin)
        assertEquals(2600, f.populationMax)
        assertEquals("QUIET", a.commercial!!.level.name)
        assertTrue(a.commercial!!.isDefaulted)
        assertEquals(observed.minusMinutes(5).toString(), a.commercial!!.observedAt)
    }

    @Test
    fun `예보·상권·낡은 시각이 없으면 null이다`() {
        val area = CongestionAreaView("POI1", "이름", CongestionSnapshot(CongestionLevel.RELAXED, 1, 2, observed), forecast = null, commercial = null)
        val gql = SeoulCongestionView(areas = listOf(area), degraded = false, staleSince = null).toGql()

        assertFalse(gql.degraded)
        assertNull(gql.staleSince)
        assertNull(gql.areas.single().forecast)
        assertNull(gql.areas.single().commercial)
    }

    @Test
    fun `구역 경계는 필드마다 옮겨진다`() {
        val gql = CongestionAreaBoundaryView("POI2", "명동", "관광특구", listOf(listOf(126.98, 37.56), listOf(126.99, 37.57))).toGql()

        assertEquals("POI2", gql.id)
        assertEquals("명동", gql.name)
        assertEquals("관광특구", gql.category)
        assertEquals(listOf(listOf(126.98, 37.56), listOf(126.99, 37.57)), gql.polygon)
    }

    @Test
    fun `뷰포트 입력은 위도·경도 경계를 뒤바꾸지 않는다`() {
        val bounds = GqlMapBoundsInput(minLat = 37.4, minLng = 126.9, maxLat = 37.6, maxLng = 127.1).toBounds()

        assertEquals(37.4, bounds.minLat)
        assertEquals(126.9, bounds.minLng)
        assertEquals(37.6, bounds.maxLat)
        assertEquals(127.1, bounds.maxLng)
    }

    @Test
    fun `뒤집힌 뷰포트는 INVALID_BBOX다`() {
        assertFailsWith<InvalidBboxException> { GqlMapBoundsInput(minLat = 37.6, minLng = 126.9, maxLat = 37.4, maxLng = 127.1).toBounds() }
    }
}
