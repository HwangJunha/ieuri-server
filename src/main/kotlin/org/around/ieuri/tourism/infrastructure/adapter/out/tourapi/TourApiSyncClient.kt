package org.around.ieuri.tourism.infrastructure.adapter.out.tourapi

import org.around.ieuri.tourism.application.port.out.client.SpotSyncPage
import org.around.ieuri.tourism.application.port.out.client.SpotSyncPort
import org.around.ieuri.tourism.application.command.SyncSpotCommand
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.infrastructure.config.TourApiProperties
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.requiredBody
import org.springframework.web.util.UriComponentsBuilder

/**
 * 전량 동기화용 TourAPI 어댑터(areaBasedSyncList2). [TourApiClient]와 같은 규율을 따른다 — TourAPI 세부
 * (필드명·코드 체계)를 여기 가두고, 밖으로는 도메인 언어로만 내보낸다.
 *
 * **[TourApiClient]와 분리한 이유.** 오퍼레이션과 페이지 크기 상한이 다르고(§4.2), 조회 경로는 R-8 이후
 * TourAPI를 전혀 쓰지 않게 되는 반면 이 경로는 남는다. 한 클래스에 두면 수명이 다른 둘이 묶인다.
 *
 * `arrange=Q`(수정일순 + **대표이미지 필수**)로 수집하므로 이미지 없는 항목은 upstream 단계에서 이미
 * 걸러진다(R-6). 다만 그 보장에만 기대지 않고 매핑에서도 한 번 더 드랍한다 — `thumbnailImageUrl: String!`의
 * 보장이 요청 시점 필터에서 쓰기 시점 규율로 옮겨왔기 때문이다(런타임 계약 위험 1번).
 */
@Component
class TourApiSyncClient(
    private val properties: TourApiProperties,
    private val tourApiRestClient: RestClient,
) : SpotSyncPort {

    override fun fetchPage(language: DomainLocale, pageNo: Int, numOfRows: Int): SpotSyncPage {
        val uri = UriComponentsBuilder.fromUriString(properties.baseUrls.getValue(language.name))
            .path("/areaBasedSyncList2")
            .queryParam("serviceKey", properties.serviceKey)
            .queryParam("MobileOS", properties.mobileOs)
            .queryParam("MobileApp", properties.mobileApp)
            .queryParam("_type", "json")
            .queryParam("numOfRows", numOfRows)
            .queryParam("pageNo", pageNo)
            .queryParam("arrange", properties.arrange)
            .build(true)
            .toUri()

        val response: TourApiResponse = tourApiRestClient.get().uri(uri).retrieve().requiredBody()
        return mapPage(response, language)
    }

    /** 응답 → 적재 레코드. HTTP·역직렬화와 분리해 단위 테스트 가능(TourApiClient.mapResponse와 같은 관례). */
    fun mapPage(response: TourApiResponse, language: DomainLocale): SpotSyncPage {
        val body = response.response.body
        var fallbacks = 0

        val records = body.items.item.mapNotNull { item ->
            val resolved = resolveCategory(item.contenttypeid, language)
            if (resolved.isFallback) fallbacks++
            item.toRecord(language, resolved.category)
        }

        return SpotSyncPage(
            records = records,
            totalCount = body.totalCount,
            droppedCount = body.items.item.size - records.size,
            categoryFallbackCount = fallbacks,
        )
    }

    /**
     * 비관광 항목 제외(lclsSystm2)는 설정이 빈 목록이면 아무것도 걸러내지 않는다. `exclude-lclssystm2`는
     * SPEC-0002에서 `[TBD]`로 넘어온 상태이며 이 스펙이 해소하지 않는다 — 다만 전량 적재 후
     * `content_type_id`·분포를 질의하면 표본이 나오므로 그때 확정할 수 있다(설계 문서 §4.3).
     */
    private fun TourApiResponse.Item.toRecord(language: DomainLocale, category: SpotCategory): SyncSpotCommand? {
        if (lclsSystm2 != null && lclsSystm2 in properties.excludeLclssystm2) return null
        return SyncSpotCommand(
            contentId = contentid?.takeIf { it.isNotBlank() } ?: return null,
            language = language,
            title = title?.takeIf { it.isNotBlank() } ?: return null,
            address = listOfNotNull(addr1, addr2).joinToString(" ").ifBlank { null },
            // arrange=Q가 보장하더라도 재확인한다 — 이 필드는 SDL에서 non-null이다.
            thumbnailImageUrl = (firstimage?.takeIf { it.isNotBlank() } ?: return null).toHttps(),
            coordinates = coordinatesOrNull(mapy ?: return null, mapx ?: return null) ?: return null,
            category = category,
            contentTypeId = contenttypeid?.toIntOrNull(),
            copyright = CopyrightType.fromCode(cpyrhtDivCd),
        )
    }

    /**
     * 좌표 범위를 벗어난 항목은 적재하지 않는다. 도메인 [Coordinates]가 던지는 예외를 배치 전체를
     * 중단시키는 대신 항목 드랍으로 흡수한다 — 한 건의 이상 좌표가 페이지 전체를 실패시킬 이유가 없다.
     */
    private fun coordinatesOrNull(latitude: Double, longitude: Double): Coordinates? =
        runCatching { Coordinates(latitude = latitude, longitude = longitude) }.getOrNull()

    /**
     * contentTypeId → 도메인 카테고리. 해석 실패 시 TOURIST_SPOT으로 떨어지는 것은 [TourApiClient]와
     * 동일한 동작이지만, **폴백이었다는 사실을 함께 돌려준다.** 요청 단위에서는 간헐적이던 폴백이
     * 배치에서는 대량으로 적용될 수 있어 관측이 필요하기 때문이다(런타임 계약 위험 4번).
     */
    private fun resolveCategory(contentTypeId: String?, locale: DomainLocale): ResolvedCategory {
        val codeset = if (locale.isKorean) TourApiProperties.CODESET_KO else TourApiProperties.CODESET_FOREIGN
        val id = contentTypeId?.toIntOrNull() ?: return ResolvedCategory(SpotCategory.TOURIST_SPOT, isFallback = true)
        val matched = properties.categoryCodes[codeset]?.entries?.firstOrNull { it.value == id }?.key
        return matched?.let { ResolvedCategory(it, isFallback = false) }
            ?: ResolvedCategory(SpotCategory.TOURIST_SPOT, isFallback = true)
    }

    private data class ResolvedCategory(val category: SpotCategory, val isFallback: Boolean)

    private fun String.toHttps(): String = replaceFirst("http://", "https://")
}
