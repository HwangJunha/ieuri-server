package org.around.ieuri.tourism.adapter.client.tourapi.mapper

import org.around.ieuri.tourism.adapter.client.tourapi.TourApiFestivalResponse
import org.around.ieuri.tourism.adapter.client.tourapi.TourApiResponse
import org.around.ieuri.tourism.application.spot.required.NearbySpotData
import org.around.ieuri.tourism.domain.catalog.CatalogStandardValues
import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.Coordinates
import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.FestivalPeriod
import org.around.ieuri.tourism.domain.spot.Spot
import org.around.ieuri.tourism.domain.spot.SpotFestival
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** locationBasedList2 item → 중립 [NearbySpotData]. 필수 필드가 비면 null로 드랍한다. */
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
 * areaBasedSyncList2 item → [Spot]. 필수 필드가 비거나 좌표가 범위를 벗어나면 null로 드랍한다.
 * - `showflag`가 `"1"`이 아니면(결측 포함) 드랍한다(SPEC-0029 R-2).
 */
fun TourApiResponse.Item.toSpotOrNull(
    language: DomainLocale,
    category: SpotCategory,
): Spot? {
    if (showflag?.trim() != SHOWFLAG_VISIBLE) return null

    return Spot(
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

/** searchFestival2 item → [SpotFestival]. [asOf] 기준 종료됐거나 날짜 해석에 실패하면 null로 드랍한다. */
fun TourApiFestivalResponse.Item.toSpotFestivalOrNull(
    language: DomainLocale,
    asOf: LocalDate,
): SpotFestival? {
    val id = contentid?.takeIf { it.isNotBlank() } ?: return null
    val start = parseBasicIsoDate(eventstartdate) ?: return null
    val end = parseBasicIsoDate(eventenddate) ?: return null
    val period = runCatching { FestivalPeriod(start = start, end = end) }.getOrNull() ?: return null
    if (period.isEndedAsOf(asOf)) return null

    return SpotFestival(
        contentId = id,
        language = language,
        period = period,
        progressType = progresstype?.takeIf { it.isNotBlank() },
        festivalType = festivaltype?.takeIf { it.isNotBlank() },
    )
}

/**
 * 적재 가능한 좌표만 통과시킨다. 아니면 null이다(도메인 예외도 드랍으로 흡수).
 * - 한국 범위를 여기서 본다 — `(0,0)`이나 상류 플레이스홀더 좌표를 들이지 않는다.
 */
private fun coordinatesOrNull(latitude: Double, longitude: Double): Coordinates? {
    if (!CatalogStandardValues.isValidCoordinate(mapX = longitude, mapY = latitude)) return null
    return runCatching { Coordinates(latitude = latitude, longitude = longitude) }.getOrNull()
}

/** `YYYYMMDD` 파싱. 다른 형식이나 존재하지 않는 날짜는 보정하지 않고 null이다. */
private fun parseBasicIsoDate(raw: String?): LocalDate? {
    val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching { LocalDate.parse(trimmed, DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull()
}
