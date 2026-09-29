package org.around.ieuri.tourism.infrastructure.mapper

import org.around.ieuri.tourism.domain.vo.CommercialActivityLevel
import org.around.ieuri.tourism.domain.vo.CongestionLevel
import org.around.ieuri.tourism.infrastructure.adapter.out.client.seoulopendata.SeoulOpenDataResponse
import org.around.ieuri.tourism.infrastructure.adapter.out.client.seoulopendata.mapper.toEventViewOrNull
import org.around.ieuri.tourism.infrastructure.adapter.out.client.seoulopendata.mapper.toForecastOrNull
import org.around.ieuri.tourism.infrastructure.adapter.out.client.seoulopendata.mapper.toSnapshotOrNull
import org.around.ieuri.tourism.infrastructure.adapter.out.client.seoulopendata.mapper.toStatusOrNull
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 서울 실시간 도시데이터 응답 → 도메인 매핑(POL-033, SPEC-0014 R7·R9·R10·R12).
 * - 필드마다 다른 값을 넣는다 — 특히 `EVENT_X`(경도)·`EVENT_Y`(위도)는 뒤집기 쉽다.
 */
class SeoulOpenDataMapperTest {

    // ---------- 인구 블록 ----------

    @Test
    fun `인구 블록이 필드별로 매핑된다`() {
        val population = SeoulOpenDataResponse.Population(
            congestionLevel = "약간 붐빔",
            populationMin = "3000",
            populationMax = "3500",
            observedAt = "2026-08-10 05:45",
        )

        val snapshot = population.toSnapshotOrNull()

        assertNotNull(snapshot)
        assertEquals(CongestionLevel.SLIGHTLY_CROWDED, snapshot.level)
        assertEquals(3000, snapshot.populationMin)
        assertEquals(3500, snapshot.populationMax)
        assertEquals(LocalDateTime.parse("2026-08-10T05:45"), snapshot.observedAt)
    }

    @Test
    fun `혼잡 단계가 정의되지 않으면 스냅샷을 만들지 않는다`() {
        // AC R7. 임의 단계로 넘기지 않는다.
        val population = SeoulOpenDataResponse.Population(
            congestionLevel = "매우 붐빔",
            populationMin = "3000",
            populationMax = "3500",
            observedAt = "2026-08-10 05:45",
        )

        assertNull(population.toSnapshotOrNull())
    }

    @Test
    fun `시각이 없으면 스냅샷을 만들지 않는다`() {
        // 기준 시각 없는 스냅샷은 R8을 만족할 수 없다.
        val population = SeoulOpenDataResponse.Population(
            congestionLevel = "여유",
            populationMin = "3000",
            populationMax = "3500",
            observedAt = null,
        )

        assertNull(population.toSnapshotOrNull())
    }

    @Test
    fun `인구 값이 깨져도 구역 전체를 버리지 않는다`() {
        // 인구 범위 하나가 깨져도 0으로 두고 나머지를 살린다.
        val population = SeoulOpenDataResponse.Population(
            congestionLevel = "여유",
            populationMin = "삼천",
            populationMax = null,
            observedAt = "2026-08-10 05:45",
        )

        val snapshot = population.toSnapshotOrNull()

        assertNotNull(snapshot)
        assertEquals(0, snapshot.populationMin)
        assertEquals(0, snapshot.populationMax)
    }

    // ---------- 예보 ----------

    @Test
    fun `예보가 있으면 전부 매핑된다`() {
        // AC R9.
        val population = SeoulOpenDataResponse.Population(
            congestionLevel = "여유",
            populationMin = "1",
            populationMax = "2",
            observedAt = "2026-08-10 05:45",
            forecastAvailable = "Y",
            forecast = listOf(
                SeoulOpenDataResponse.Forecast("2026-08-10 07:00", "보통", "5500", "6000"),
                SeoulOpenDataResponse.Forecast("2026-08-10 08:00", "붐빔", "10000", "12000"),
            ),
        )

        val forecast = population.toForecastOrNull()

        assertNotNull(forecast)
        assertEquals(2, forecast.size)
        assertEquals(LocalDateTime.parse("2026-08-10T07:00"), forecast[0].forecastTime)
        assertEquals(CongestionLevel.NORMAL, forecast[0].level)
        assertEquals(5500, forecast[0].populationMin)
        assertEquals(6000, forecast[0].populationMax)
        assertEquals(CongestionLevel.CROWDED, forecast[1].level)
    }

    @Test
    fun `예보가 없으면 null이며 오류가 아니다`() {
        // AC R9. FCST_YN=N이면 부재다.
        val population = SeoulOpenDataResponse.Population(
            congestionLevel = "여유",
            observedAt = "2026-08-10 05:45",
            forecastAvailable = "N",
        )

        assertNull(population.toForecastOrNull())
    }

    @Test
    fun `깨진 예보 항목만 버리고 나머지는 살린다`() {
        val population = SeoulOpenDataResponse.Population(
            congestionLevel = "여유",
            observedAt = "2026-08-10 05:45",
            forecastAvailable = "Y",
            forecast = listOf(
                SeoulOpenDataResponse.Forecast("2026-08-10 07:00", "보통", "1", "2"),
                SeoulOpenDataResponse.Forecast("2026-08-10 08:00", "알 수 없음", "1", "2"),
            ),
        )

        assertEquals(1, population.toForecastOrNull()?.size)
    }

    // ---------- 상권 ----------

    @Test
    fun `상권이 필드별로 매핑되고 실측이면 기본값 표식이 거짓이다`() {
        // AC R10. CMRCL_TIME은 인구와 다른 형식("20260810 0600")이다.
        val commercial = SeoulOpenDataResponse.Commercial(
            level = "바쁜",
            paymentCount = "42",
            observedAt = "20260810 0600",
        )

        val status = commercial.toStatusOrNull()

        assertNotNull(status)
        assertEquals(CommercialActivityLevel.BUSY, status.level)
        assertEquals(false, status.isDefaulted)
        assertEquals(LocalDateTime.parse("2026-08-10T06:00"), status.observedAt)
    }

    @Test
    fun `결제 건수가 0인 보통은 기본값으로 본다`() {
        // AC R10. 기준선이 없는 구역에 업스트림이 "보통"을 고정 반환하는 것을 구분한다.
        val commercial = SeoulOpenDataResponse.Commercial(
            level = "보통",
            paymentCount = "0",
            observedAt = "20260810 0600",
        )

        assertEquals(true, commercial.toStatusOrNull()?.isDefaulted)
    }

    @Test
    fun `결제 건수가 있는 보통은 실측이다`() {
        // AC R10. 같은 NORMAL이라도 실측과 자리표시자가 갈린다.
        val commercial = SeoulOpenDataResponse.Commercial(
            level = "보통",
            paymentCount = "7",
            observedAt = "20260810 0600",
        )

        assertEquals(false, commercial.toStatusOrNull()?.isDefaulted)
    }

    @Test
    fun `보통이 아니면 결제 건수가 없어도 실측이다`() {
        // 단계가 "보통"이 아니라는 것 자체가 실측 신호다.
        val commercial = SeoulOpenDataResponse.Commercial(
            level = "분주한",
            paymentCount = null,
            observedAt = "20260810 0600",
        )

        assertEquals(false, commercial.toStatusOrNull()?.isDefaulted)
    }

    @Test
    fun `상권 단계를 매핑할 수 없으면 부재다`() {
        // AC R10. 부재는 오류가 아니다.
        val commercial = SeoulOpenDataResponse.Commercial(level = null, observedAt = "20260810 0600")

        assertNull(commercial.toStatusOrNull())
    }

    // ---------- 문화행사 ----------

    @Test
    fun `문화행사가 필드별로 매핑되고 좌표가 뒤집히지 않는다`() {
        // AC R12. EVENT_X가 경도, EVENT_Y가 위도다.
        val event = SeoulOpenDataResponse.Event(
            title = "청춘만발",
            period = "2026-08-10~2026-08-21",
            place = "국립정동극장 세실",
            longitude = "126.9",
            latitude = "37.5",
            url = "https://culture.seoul.go.kr/e/1",
        )

        val view = event.toEventViewOrNull()

        assertNotNull(view)
        assertEquals("청춘만발", view.title)
        assertEquals("2026-08-10~2026-08-21", view.period)
        assertEquals("국립정동극장 세실", view.place)
        assertEquals(126.9, view.lng)
        assertEquals(37.5, view.lat)
        assertEquals("https://culture.seoul.go.kr/e/1", view.url)
    }

    @Test
    fun `좌표가 없는 행사는 버린다`() {
        // 지도에 찍을 수 없다.
        val event = SeoulOpenDataResponse.Event(title = "축제", longitude = null, latitude = "37.5")

        assertNull(event.toEventViewOrNull())
    }

    @Test
    fun `제목이 없는 행사는 버린다`() {
        val event = SeoulOpenDataResponse.Event(title = "  ", longitude = "126.9", latitude = "37.5")

        assertNull(event.toEventViewOrNull())
    }

    @Test
    fun `선택 필드가 비면 빈 문자열과 null로 채워진다`() {
        // place·period는 non-null이라 빈 문자열이 되고, url만 null이 된다.
        val event = SeoulOpenDataResponse.Event(
            title = "축제",
            period = null,
            place = null,
            longitude = "126.9",
            latitude = "37.5",
            url = "",
        )

        val view = event.toEventViewOrNull()

        assertNotNull(view)
        assertEquals("", view.place)
        assertEquals("", view.period)
        assertNull(view.url)
    }

    @Test
    fun `같은 행사는 같은 id를 갖는다`() {
        // 여러 구역 응답에 걸친 같은 행사의 중복을 거른다.
        val event = SeoulOpenDataResponse.Event(
            title = "축제",
            longitude = "126.9",
            latitude = "37.5",
        )

        assertEquals(event.toEventViewOrNull()?.id, event.copy(url = "https://x").toEventViewOrNull()?.id)
    }
}
