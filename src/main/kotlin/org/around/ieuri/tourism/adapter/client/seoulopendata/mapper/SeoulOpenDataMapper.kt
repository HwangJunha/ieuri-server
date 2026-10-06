package org.around.ieuri.tourism.adapter.client.seoulopendata.mapper

import org.around.ieuri.tourism.adapter.client.seoulopendata.SeoulOpenDataResponse
import org.around.ieuri.tourism.application.shared.provided.dto.CulturalEventView
import org.around.ieuri.tourism.application.shared.required.SeoulOpenDataSnapshot
import org.around.ieuri.tourism.domain.shared.CommercialActivityLevel
import org.around.ieuri.tourism.domain.shared.CommercialStatus
import org.around.ieuri.tourism.domain.shared.CongestionForecast
import org.around.ieuri.tourism.domain.shared.CongestionLevel
import org.around.ieuri.tourism.domain.shared.CongestionSnapshot
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/*
 * 서울 실시간 도시데이터 응답 → 도메인 변환(POL-033).
 * - 인구 값은 관대하게 파싱하고, 시각은 두 형식을 모두 시도한다.
 * - 정의된 4값이 아닌 혼잡 단계는 임의 단계로 넘기지 않고 매핑 실패로 처리한다(R7).
 */

/** `"2026-08-10 05:45"`(인구)와 `"20260810 0600"`(상권) 두 형식을 모두 읽는다. */
private val TIME_FORMATS = listOf(
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
    DateTimeFormatter.ofPattern("yyyyMMdd HHmm"),
)

/**
 * 응답 전체 → 스냅샷. 요청한 구역과 응답의 `AREA_CD`가 다르면 null이다(R15).
 * - 없는 구역명을 요청해도 다른 구역 데이터가 온다 — 이 검증이 없으면 잘못된 데이터가 올바른 키로 캐시된다.
 * - 불일치 로깅은 호출자가 한다.
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

/** 인구 블록 → 스냅샷. 단계 매핑 실패나 값 누락이면 null이며, 호출자는 그 구역을 건너뛴다. */
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

/** 예보 12건. `FCST_YN`이 `Y`가 아니면 null이다(부재, R9). 매핑에 실패한 항목만 버린다. */
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
 * 상권 블록 → 상태(R10). 블록이 없거나 단계를 매핑할 수 없으면 null(부재)이다.
 * - [CommercialStatus.isDefaulted]는 단계가 `보통`이고 결제 건수가 0이거나 없을 때만 참이다.
 */
fun SeoulOpenDataResponse.Commercial.toStatusOrNull(): CommercialStatus? {
    val mapped = CommercialActivityLevel.fromUpstreamOrNull(level) ?: return null
    val observed = parseTimeOrNull(observedAt) ?: return null
    val defaulted = mapped == CommercialActivityLevel.NORMAL && paymentCount.toIntOrZero() == 0
    return CommercialStatus(level = mapped, isDefaulted = defaulted, observedAt = observed)
}

/**
 * 문화행사 → 마커. 좌표나 제목이 없으면 버린다.
 * - `EVENT_X`가 경도, `EVENT_Y`가 위도다.
 * - id는 제목과 좌표로 만든다 — 여러 구역 응답에 걸친 같은 행사의 중복을 거른다.
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

/** 인구·결제 값 파싱. 실패하면 예외 대신 0이다. */
private fun String?.toIntOrZero(): Int = this?.trim()?.toDoubleOrNull()?.toInt() ?: 0
