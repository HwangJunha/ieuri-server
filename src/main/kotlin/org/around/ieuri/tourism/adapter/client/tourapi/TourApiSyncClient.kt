package org.around.ieuri.tourism.adapter.client.tourapi

import org.around.ieuri.tourism.adapter.client.tourapi.mapper.toSpotOrNull
import org.around.ieuri.tourism.adapter.config.TourApiProperties
import org.around.ieuri.tourism.application.category.provided.CategoryMappingQueryUseCase
import org.around.ieuri.tourism.application.category.required.CategoryExternalMappingPort
import org.around.ieuri.tourism.application.spot.required.SpotSyncPort
import org.around.ieuri.tourism.application.spot.required.dto.SpotSyncPage
import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.spot.spec.SpotExclusionRule
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.requiredBody
import org.springframework.web.util.UriComponentsBuilder

/**
 * 전량 동기화용 TourAPI 어댑터(areaBasedSyncList2). TourAPI 세부를 여기 가두고 도메인 언어로만 내보낸다.
 * - `arrange=Q`(대표이미지 필수)·`showflag=1`로 요청하지만 매핑에서도 한 번 더 드랍한다(R-6).
 */
@Component
class TourApiSyncClient(
    private val properties: TourApiProperties,
    private val tourApiRestClient: RestClient,
    private val exclusionRule: SpotExclusionRule,
    private val categoryMapping: CategoryMappingQueryUseCase,
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
            .queryParam("showflag", properties.showflag)
            .build(true)
            .toUri()

        val response: TourApiResponse = tourApiRestClient.get().uri(uri).retrieve().requiredBody()
        return mapPage(response, language)
    }

    /** 응답 → 적재 레코드. HTTP·역직렬화와 분리해 단위 테스트 가능하게 둔다. */
    fun mapPage(response: TourApiResponse, language: DomainLocale): SpotSyncPage {
        val body = response.response.body
        var fallbacks = 0
        var excluded = 0
        // 페이지 안에서 contentTypeId → 카테고리를 한 번만 읽는다.
        val categoryMemo = mutableMapOf<String, SpotCategory?>()

        val records = body.items.item.mapNotNull { item ->
            val resolved = resolveCategory(item.contenttypeid, language, categoryMemo)
            if (resolved.isFallback) fallbacks++
            // 비관광 항목 제외(lclsSystm2). 설정이 빈 목록이면 아무것도 거르지 않는다.
            if (item.lclsSystm2 != null && item.lclsSystm2 in properties.excludeLclssystm2) return@mapNotNull null
            // 제목 키워드 기반 제외(SPEC-0018). 규칙이 관광 카테고리를 보호하므로 카테고리 해석 뒤에 판정한다.
            if (item.title != null && exclusionRule.isExcluded(item.title, resolved.category)) {
                excluded++
                return@mapNotNull null
            }
            item.toSpotOrNull(language, resolved.category)
        }

        return SpotSyncPage(
            records = records,
            totalCount = body.totalCount,
            // 의도된 제외는 필드 결손(dropped)과 따로 센다.
            droppedCount = body.items.item.size - records.size - excluded,
            categoryFallbackCount = fallbacks,
            excludedCount = excluded,
        )
    }

    /** contentTypeId → 도메인 카테고리. 해석 실패는 TOURIST_SPOT으로 떨어지며, 폴백 여부를 함께 돌려준다. */
    private fun resolveCategory(
        contentTypeId: String?,
        locale: DomainLocale,
        memo: MutableMap<String, SpotCategory?>,
    ): ResolvedCategory {
        val codeset = if (locale.isKorean) TourApiProperties.CODESET_KO else TourApiProperties.CODESET_FOREIGN
        if (contentTypeId == null) return ResolvedCategory(SpotCategory.TOURIST_SPOT, isFallback = true)
        // 같은 코드가 반복되므로 페이지 안에서 한 번만 해석한다.
        val matched = memo.getOrPut(contentTypeId) {
            categoryMapping.categoryOf(contentTypeId, CategoryExternalMappingPort.PROVIDER_TOUR_API, codeset)
        }
        return matched?.let { ResolvedCategory(it, isFallback = false) }
            ?: ResolvedCategory(SpotCategory.TOURIST_SPOT, isFallback = true)
    }

    private data class ResolvedCategory(val category: SpotCategory, val isFallback: Boolean)
}
