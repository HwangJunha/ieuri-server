package org.around.ieuri.tourism.infrastructure.adapter.out.client.tourapi

import org.around.ieuri.tourism.application.port.out.client.SpotSyncPage
import org.around.ieuri.tourism.application.port.out.client.SpotSyncPort
import org.around.ieuri.tourism.domain.specification.SpotExclusionRule
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.application.port.out.query.CategoryExternalMappingPort
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.infrastructure.adapter.out.client.tourapi.mapper.toSyncSpotCommandOrNull
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
    private val exclusionRule: SpotExclusionRule,
    private val categoryMapping: CategoryExternalMappingPort,
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
        var excluded = 0
        // 페이지 안에서 contentTypeId → 카테고리를 한 번만 읽는다.
        val categoryMemo = mutableMapOf<String, SpotCategory?>()

        val records = body.items.item.mapNotNull { item ->
            val resolved = resolveCategory(item.contenttypeid, language, categoryMemo)
            if (resolved.isFallback) fallbacks++
            // 비관광 항목 제외(lclsSystm2). 설정이 빈 목록이면 아무것도 걸러내지 않는다.
            if (item.lclsSystm2 != null && item.lclsSystm2 in properties.excludeLclssystm2) return@mapNotNull null
            // 제목 키워드 기반 제외(SPEC-0018). 약국·편의점·의원처럼 관광 목적이 아닌 항목이며,
            // lclsSystm2가 없는 행까지 덮는다. **카테고리 해석 이후에 판정한다** — 규칙이
            // 관광 카테고리를 보호하므로 카테고리가 정해져 있어야 한다.
            if (item.title != null && exclusionRule.isExcluded(item.title, resolved.category)) {
                excluded++
                return@mapNotNull null
            }
            item.toSyncSpotCommandOrNull(language, resolved.category)
        }

        return SpotSyncPage(
            records = records,
            totalCount = body.totalCount,
            // 제외분은 dropped와 따로 센다 — 필드 결손(dropped)과 의도된 제외는 성격이 다르고,
            // 제외 건수가 갑자기 늘면 키워드 설정이 잘못됐다는 신호다.
            droppedCount = body.items.item.size - records.size - excluded,
            categoryFallbackCount = fallbacks,
            excludedCount = excluded,
        )
    }

    /**
     * contentTypeId → 도메인 카테고리. 해석 실패 시 TOURIST_SPOT으로 떨어지는 것은 [TourApiClient]와
     * 동일한 동작이지만, **폴백이었다는 사실을 함께 돌려준다.** 요청 단위에서는 간헐적이던 폴백이
     * 배치에서는 대량으로 적용될 수 있어 관측이 필요하기 때문이다(런타임 계약 위험 4번).
     */
    private fun resolveCategory(
        contentTypeId: String?,
        locale: DomainLocale,
        memo: MutableMap<String, SpotCategory?>,
    ): ResolvedCategory {
        val codeset = if (locale.isKorean) TourApiProperties.CODESET_KO else TourApiProperties.CODESET_FOREIGN
        if (contentTypeId == null) return ResolvedCategory(SpotCategory.TOURIST_SPOT, isFallback = true)
        // 한 페이지에 같은 코드가 수십 번 나오므로 페이지 안에서 접는다 — 없으면 행마다 질의가 붙는다.
        val matched = memo.getOrPut(contentTypeId) {
            categoryMapping.categoryOf(contentTypeId, CategoryExternalMappingPort.PROVIDER_TOUR_API, codeset)
        }
        return matched?.let { ResolvedCategory(it, isFallback = false) }
            ?: ResolvedCategory(SpotCategory.TOURIST_SPOT, isFallback = true)
    }

    private data class ResolvedCategory(val category: SpotCategory, val isFallback: Boolean)
}
