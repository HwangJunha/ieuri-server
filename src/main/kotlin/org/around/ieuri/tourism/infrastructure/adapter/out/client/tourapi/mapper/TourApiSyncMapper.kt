package org.around.ieuri.tourism.infrastructure.adapter.out.client.tourapi.mapper

import org.around.ieuri.tourism.application.command.SyncFestivalCommand
import org.around.ieuri.tourism.application.command.SyncSpotCommand
import org.around.ieuri.tourism.application.port.out.client.NearbySpotData
import org.around.ieuri.tourism.domain.aggregate.CatalogStandardValues
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.FestivalPeriod
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.infrastructure.adapter.out.client.tourapi.TourApiFestivalResponse
import org.around.ieuri.tourism.infrastructure.adapter.out.client.tourapi.TourApiResponse
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * locationBasedList2 item을 중립 [NearbySpotData]로 옮긴다. 필수 필드가 비면 null로 드랍한다.
 *
 * [category]를 인자로 받는다 — contentTypeId 해석은 언어별 코드표에 의존하므로 클라이언트가 정한다.
 */
fun TourApiResponse.Item.toNearbySpotDataOrNull(category: SpotCategory): NearbySpotData? = NearbySpotData(
    id = contentid ?: return null,
    title = title ?: return null,
    address = listOfNotNull(addr1, addr2).joinToString(" ").ifBlank { null },
    thumbnailImageUrl = (firstimage ?: return null).toHttps(),
    coordinates = Coordinates(latitude = mapy ?: return null, longitude = mapx ?: return null),
    category = category,
    copyright = CopyrightType.fromCode(cpyrhtDivCd),
)

/**
 * areaBasedSyncList2 item을 적재 [SyncSpotCommand]로 옮긴다. 필수 필드가 비거나 좌표가 범위를 벗어나면
 * null로 드랍한다 — 한 건의 이상 데이터가 페이지 전체를 실패시키지 않는다.
 *
 * `showflag`가 `"1"`이 아니면 드랍한다. **결측도 드랍이다** — `"1"`로 간주하면 표출이 중단된
 * 항목이 그대로 들어온다. 요청에 `showflag=1`을 넘겨도 여기서 다시 본다(SPEC-0029 R-2).
 */
fun TourApiResponse.Item.toSyncSpotCommandOrNull(
    language: DomainLocale,
    category: SpotCategory,
): SyncSpotCommand? {
    if (showflag?.trim() != SHOWFLAG_VISIBLE) return null

    return SyncSpotCommand(
        contentId = contentid?.takeIf { it.isNotBlank() } ?: return null,
        language = language,
        title = title?.takeIf { it.isNotBlank() } ?: return null,
        address = listOfNotNull(addr1, addr2).joinToString(" ").ifBlank { null },
        thumbnailImageUrl = (firstimage?.takeIf { it.isNotBlank() } ?: return null).toHttps(),
        coordinates = coordinatesOrNull(mapy ?: return null, mapx ?: return null) ?: return null,
        category = category,
        contentTypeId = contenttypeid?.toIntOrNull() ?: return null,
        copyright = CopyrightType.fromCode(cpyrhtDivCd),
    )
}

/** 표출 중인 항목의 `showflag` 값. 이 값이 아니면(결측 포함) 적재하지 않는다. */
const val SHOWFLAG_VISIBLE: String = "1"

/**
 * searchFestival2 item을 적재 [SyncFestivalCommand]로 옮긴다. [asOf] 기준으로 이미 종료된 축제와
 * 날짜 해석에 실패한 항목은 null로 드랍한다.
 */
fun TourApiFestivalResponse.Item.toSyncFestivalCommandOrNull(
    language: DomainLocale,
    asOf: LocalDate,
): SyncFestivalCommand? {
    val id = contentid?.takeIf { it.isNotBlank() } ?: return null
    val start = parseBasicIsoDate(eventstartdate) ?: return null
    val end = parseBasicIsoDate(eventenddate) ?: return null
    val period = runCatching { FestivalPeriod(start = start, end = end) }.getOrNull() ?: return null
    if (period.isEndedAsOf(asOf)) return null

    return SyncFestivalCommand(
        contentId = id,
        language = language,
        period = period,
        progressType = progresstype?.takeIf { it.isNotBlank() },
        festivalType = festivaltype?.takeIf { it.isNotBlank() },
    )
}

/**
 * 적재 가능한 좌표만 통과시킨다. 아니면 null — 도메인이 던지는 예외도 항목 드랍으로 흡수한다.
 *
 * **한국 범위를 여기서 본다.** [Coordinates]는 지구 전체를 유효로 보므로 `(0,0)`(기니만)이나
 * 상류가 좌표를 모를 때 넣는 플레이스홀더 `(117.99, 19.69)`(남중국해)가 그대로 통과한다.
 * 그런 행은 적재해 봐야 배정에서 빠져 "존재하지만 아무 데도 안 나오는" spot이 될 뿐이라,
 * 애초에 들이지 않는다(실측 79건).
 */
private fun coordinatesOrNull(latitude: Double, longitude: Double): Coordinates? {
    if (!CatalogStandardValues.isValidCoordinate(mapX = longitude, mapY = latitude)) return null
    return runCatching { Coordinates(latitude = latitude, longitude = longitude) }.getOrNull()
}

/**
 * `YYYYMMDD` 파싱. 다른 형식이나 존재하지 않는 날짜는 null이다 — 관대하게 보정하면 잘못 해석된
 * 종료일로 정리 판정이 조용히 틀린다.
 */
private fun parseBasicIsoDate(raw: String?): LocalDate? {
    val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching { LocalDate.parse(trimmed, DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull()
}
