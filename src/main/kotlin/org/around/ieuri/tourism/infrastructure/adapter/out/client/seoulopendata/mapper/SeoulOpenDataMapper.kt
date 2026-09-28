package org.around.ieuri.tourism.infrastructure.adapter.out.client.seoulopendata.mapper

import org.around.ieuri.tourism.application.port.out.client.SeoulOpenDataSnapshot
import org.around.ieuri.tourism.application.query.CulturalEventView
import org.around.ieuri.tourism.domain.vo.CommercialActivityLevel
import org.around.ieuri.tourism.domain.vo.CommercialStatus
import org.around.ieuri.tourism.domain.vo.CongestionForecast
import org.around.ieuri.tourism.domain.vo.CongestionLevel
import org.around.ieuri.tourism.domain.vo.CongestionSnapshot
import org.around.ieuri.tourism.infrastructure.adapter.out.client.seoulopendata.SeoulOpenDataResponse
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/*
 * 서울 실시간 도시데이터 응답 → 도메인 변환(POL-033).
 *
 * 방어 원칙 셋(spec §3.4):
 *  - 인구 값은 문자열로 오고 타입이 일관되지 않다 → 관대하게 파싱하고, 실패하면 그 블록을 버린다.
 *  - 시각 형식이 블록마다 다르다 → 두 형식을 모두 시도한다.
 *  - 혼잡 단계가 정의된 4값이 아니면 **임의 단계로 넘기지 않는다**(R7) → 매핑 실패로 처리한다.
 */

/** `"2026-08-10 05:45"`(인구)와 `"20260810 0600"`(상권) 두 형식을 모두 읽는다. */
private val TIME_FORMATS = listOf(
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
    DateTimeFormatter.ofPattern("yyyyMMdd HHmm"),
)

/**
 * 응답 전체 → 스냅샷. **요청한 구역과 응답의 `AREA_CD`가 다르면 null이다**(R15).
 *
 * 존재하지 않는 구역명을 요청해도 오류 없이 다른 구역 데이터가 돌아오는 동작이 관측됐다
 * (2026-08-10 샘플 키에서 어떤 구역을 물어도 POI009가 왔다). 이 검증이 없으면 **잘못된
 * 구역 데이터가 올바른 키로 캐시된다** — 캐시에 들어가는 순간 원인 추적이 어려워진다.
 *
 * 불일치를 null로만 알린다. 로깅은 호출자가 한다 — 매퍼는 순수 함수로 남긴다(POL-033).
 */
fun SeoulOpenDataResponse.toSnapshotOrNull(requestedAreaCode: String): SeoulOpenDataSnapshot? {
    val city = cityData ?: return null
    if (!city.areaCode.equals(requestedAreaCode, ignoreCase = true)) return null

    val population = city.population.firstOrNull()
    return SeoulOpenDataSnapshot(
        areaCode = requestedAreaCode,
        congestion = population?.toSnapshotOrNull(),
        forecast = population?.toForecastOrNull(),
        commercial = city.commercial?.toStatusOrNull(),
        events = city.events.mapNotNull { it.toEventViewOrNull() },
    )
}

/**
 * 인구 블록 → 스냅샷. 단계 매핑 실패나 값 누락이면 null이며, 호출자는 그 구역을 건너뛴다.
 *
 * 정의되지 않은 단계를 임의 값으로 넘기지 않는 것이 요점이다 — 업스트림이 5번째 값을
 * 도입했을 때 조용히 잘못된 색을 칠하는 것보다 표시하지 않는 편이 낫다.
 */
fun SeoulOpenDataResponse.Population.toSnapshotOrNull(): CongestionSnapshot? {
    val level = CongestionLevel.fromUpstreamOrNull(congestionLevel) ?: return null
    val observed = parseTimeOrNull(observedAt) ?: return null
    return CongestionSnapshot(
        level = level,
        populationMin = populationMin.toIntOrZero(),
        populationMax = populationMax.toIntOrZero(),
        observedAt = observed,
    )
}

/**
 * 예보 12건. `FCST_YN`이 `Y`가 아니면 null(부재이며 오류가 아니다, R9).
 *
 * 개별 항목의 매핑 실패는 그 항목만 버린다 — 12건 중 하나가 깨졌다고 예보 전체를
 * 없애면 정보 손실이 더 크다.
 */
fun SeoulOpenDataResponse.Population.toForecastOrNull(): List<CongestionForecast>? {
    if (!forecastAvailable.equals("Y", ignoreCase = true)) return null
    val items = forecast.mapNotNull { it.toForecastItemOrNull() }
    return items.ifEmpty { null }
}

private fun SeoulOpenDataResponse.Forecast.toForecastItemOrNull(): CongestionForecast? {
    val level = CongestionLevel.fromUpstreamOrNull(congestionLevel) ?: return null
    val time = parseTimeOrNull(forecastTime) ?: return null
    return CongestionForecast(
        forecastTime = time,
        level = level,
        populationMin = populationMin.toIntOrZero(),
        populationMax = populationMax.toIntOrZero(),
    )
}

/**
 * 상권 블록 → 3상태 중 둘(R10). 블록 자체가 없거나 단계를 매핑할 수 없으면 null(**부재**)이다.
 *
 * [CommercialStatus.isDefaulted]는 **결제 건수가 0이거나 없을 때** 참이다. 업스트림이
 * 기준선 없는 구역에 `보통`을 고정 반환하는데 값만으로는 구분할 수 없어, 실제 결제
 * 신호가 있었는지를 근거로 삼는다. 단계가 `보통`이 아니면 실측이 있었다는 뜻이므로
 * 건수와 무관하게 거짓이다.
 */
fun SeoulOpenDataResponse.Commercial.toStatusOrNull(): CommercialStatus? {
    val mapped = CommercialActivityLevel.fromUpstreamOrNull(level) ?: return null
    val observed = parseTimeOrNull(observedAt) ?: return null
    val defaulted = mapped == CommercialActivityLevel.NORMAL && paymentCount.toIntOrZero() == 0
    return CommercialStatus(level = mapped, isDefaulted = defaulted, observedAt = observed)
}

/**
 * 문화행사 → 마커. 좌표나 제목이 없으면 버린다(지도에 찍을 수 없다).
 *
 * **`EVENT_X`가 경도, `EVENT_Y`가 위도다** — 이름이 직관과 반대라 여기서 바로잡는다.
 *
 * 업스트림에 안정적 id가 없어 제목과 좌표로 만든다. 같은 행사가 여러 구역 응답에
 * 걸쳐 나올 때 중복을 걸러내는 것이 목적이다.
 */
fun SeoulOpenDataResponse.Event.toEventViewOrNull(): CulturalEventView? {
    val name = title?.takeIf { it.isNotBlank() } ?: return null
    val lng = longitude?.toDoubleOrNull() ?: return null
    val lat = latitude?.toDoubleOrNull() ?: return null
    return CulturalEventView(
        id = "$name@$lng,$lat",
        title = name,
        place = place.orEmpty(),
        period = period.orEmpty(),
        lng = lng,
        lat = lat,
        url = url?.takeIf { it.isNotBlank() },
    )
}

/** 형식이 둘이라 순서대로 시도한다. 둘 다 아니면 null — 시각 없는 스냅샷은 쓸 수 없다. */
private fun parseTimeOrNull(raw: String?): LocalDateTime? {
    val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    for (format in TIME_FORMATS) {
        try {
            return LocalDateTime.parse(text, format)
        } catch (_: java.time.format.DateTimeParseException) {
            // 다음 형식을 시도한다.
        }
    }
    return null
}

/**
 * 인구·결제 값은 문자열로 오며 타입이 일관되지 않다. 파싱 실패를 예외로 올리지 않고
 * 0으로 둔다 — 인구 범위 하나가 깨졌다고 구역 전체를 버리는 것은 과하다.
 */
private fun String?.toIntOrZero(): Int = this?.trim()?.toDoubleOrNull()?.toInt() ?: 0
