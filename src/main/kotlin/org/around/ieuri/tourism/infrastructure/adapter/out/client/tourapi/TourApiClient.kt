package org.around.ieuri.tourism.infrastructure.adapter.out.client.tourapi

import org.around.ieuri.tourism.application.port.out.client.NearbySpotQuery
import org.around.ieuri.tourism.application.port.out.client.NearbySpotResult
import org.around.ieuri.tourism.application.port.out.client.SpotDetailQuery
import org.around.ieuri.tourism.application.port.out.client.TourInfoPort
import org.around.ieuri.tourism.domain.aggregate.SpotDetail
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.domain.vo.SpotImage
import org.around.ieuri.tourism.domain.vo.SpotInfoItem
import org.around.ieuri.tourism.domain.vo.SpotIntro
import org.around.ieuri.tourism.infrastructure.adapter.out.client.tourapi.mapper.toGallery
import org.around.ieuri.tourism.infrastructure.adapter.out.client.tourapi.mapper.toInfoItems
import org.around.ieuri.tourism.infrastructure.adapter.out.client.tourapi.mapper.toNearbySpotDataOrNull
import org.around.ieuri.tourism.infrastructure.adapter.out.client.tourapi.mapper.toSpotDetail
import org.around.ieuri.tourism.infrastructure.adapter.out.client.tourapi.mapper.toSpotIntroOrNull
import org.around.ieuri.tourism.infrastructure.config.TourApiProperties
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.requiredBody
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

/**
 * TourAPI 어댑터. TourInfoPort의 유일한 구현체로, TourAPI 세부(필드명·코드 체계)를 여기 가둔다.
 *
 * 책임은 딱 둘이다: **요청 URI 빌드**와 **응답 → 도메인/중립 DTO 매핑**. 그 외(재시도·캐시·거리계산·정렬)는
 * 여기서 하지 않는다. 캐시는 SpotDetailCache, 거리·정렬은 application이 담당한다.
 */
@Component
class TourApiClient(
    private val properties: TourApiProperties,
    private val tourApiRestClient: RestClient,
) : TourInfoPort {

    override fun findNearby(query: NearbySpotQuery): NearbySpotResult {
        val uri = UriComponentsBuilder.fromUriString(baseUrl(query.locale))
            .path("/locationBasedList2")
            .common()
            .queryParam("mapX", query.center.longitude)
            .queryParam("mapY", query.center.latitude)
            .queryParam("radius", query.radiusMeters)
            .queryParam("numOfRows", query.numOfRows)
            .queryParam("pageNo", query.pageNo)
            .queryParam("arrange", properties.arrange)
            .apply { categoryCode(query.category, query.locale)?.let { queryParam("contentTypeId", it) } }
            .build(true)
            .toUri()

        val response: TourApiResponse = tourApiRestClient.get().uri(uri).retrieve().requiredBody()
        return mapResponse(response, query.locale)
    }

    /**
     * 상세 본문(detailCommon2 1콜). 갤러리·운영정보·이용안내는 각 필드가 요청될 때 따로 가져온다.
     *
     * **본문이 없으면 null**(SPEC-0008 R-1). upstream이 결과 없음을 주면 items가 빈 리스트가 된다
     * (`"items": ""` → [CommonItemsDeserializer]가 흡수). 이전에는 `first()`가
     * NoSuchElementException을 던져 **부재가 장애로 둔갑했다** — 재시도가 무의미한 사실이 재시도
     * 가능한 실패로 표면화되고, 매 조회마다 upstream을 다시 두드렸다.
     *
     * HTTP·역직렬화 실패는 여전히 예외로 나간다(그쪽은 진짜 실패다).
     */
    override fun findDetail(query: SpotDetailQuery): SpotDetail? {
        val response: TourApiDetailCommonResponse =
            tourApiRestClient.get().uri(detailUri(query, "detailCommon2")).retrieve().requiredBody()
        val item = response.response.body.items.firstOrNull() ?: return null
        return mapDetail(item, query.locale)
    }

    /** 갤러리(detailImage2 1콜). */
    override fun findGallery(query: SpotDetailQuery): List<SpotImage> {
        val response: TourApiDetailImageResponse =
            tourApiRestClient.get().uri(detailUri(query, "detailImage2")).retrieve().requiredBody()
        return mapGallery(response.response.body.items)
    }

    /** 운영 정보(detailIntro2 1콜). 언어별 카테고리 코드가 없으면 호출 자체를 생략한다. */
    override fun findIntro(query: SpotDetailQuery, category: SpotCategory): SpotIntro? {
        val contentTypeId = categoryCode(category, query.locale) ?: return null
        val response: TourApiDetailIntroResponse =
            tourApiRestClient.get().uri(detailUri(query, "detailIntro2", contentTypeId)).retrieve().requiredBody()
        return mapIntro(response.response.body.items.firstOrNull())
    }

    /** 이용안내 항목(detailInfo2 1콜). 언어별 카테고리 코드가 없으면 호출 자체를 생략한다. */
    override fun findInfoItems(query: SpotDetailQuery, category: SpotCategory): List<SpotInfoItem> {
        val contentTypeId = categoryCode(category, query.locale) ?: return emptyList()
        val response: TourApiDetailInfoResponse =
            tourApiRestClient.get().uri(detailUri(query, "detailInfo2", contentTypeId)).retrieve().requiredBody()
        return mapInfoItems(response.response.body.items)
    }

    /** TourAPI 응답 DTO → 중립 결과. HTTP·역직렬화와 분리해 단위 테스트 가능. */
    fun mapResponse(response: TourApiResponse, locale: DomainLocale): NearbySpotResult {
        val body = response.response.body
        return NearbySpotResult(
            spots = body.items.item.mapNotNull { it.toNearbySpotDataOrNull(category(it.contenttypeid, locale)) },
            totalCount = body.totalCount,
            hasMoreUpstreamPages = body.pageNo.toLong() * body.numOfRows.toLong() < body.totalCount.toLong(),
        )
    }

    /** detailCommon2 item → 도메인 SpotDetail(본문). */
    fun mapDetail(common: TourApiDetailCommonResponse.Item, locale: DomainLocale): SpotDetail =
        common.toSpotDetail(category(common.contenttypeid, locale))

    /** detailImage2 item → 갤러리. TourAPI 반환 순서 유지, 앞 10장까지(AC-8). */
    fun mapGallery(images: List<TourApiDetailImageResponse.Item>): List<SpotImage> = images.toGallery()

    /** detailIntro2 item → 운영 정보. 표시할 값이 하나도 없으면 null(빈 껍데기 방지). */
    fun mapIntro(intro: TourApiDetailIntroResponse.Item?): SpotIntro? = intro?.toSpotIntroOrNull()

    /** detailInfo2 item → 이용안내 항목. 항목명·내용 중 하나라도 비면 제외. */
    fun mapInfoItems(info: List<TourApiDetailInfoResponse.Item>): List<SpotInfoItem> = info.toInfoItems()

    /** [contentTypeId]는 detailIntro2·detailInfo2에서 필수다(없으면 upstream이 필수 파라미터 누락으로 거절). */
    private fun detailUri(query: SpotDetailQuery, operation: String, contentTypeId: Int? = null): URI =
        UriComponentsBuilder.fromUriString(baseUrl(query.locale))
            .path("/$operation")
            .common()
            .queryParam("contentId", query.contentId)
            .apply { contentTypeId?.let { queryParam("contentTypeId", it) } }
            .build(true)
            .toUri()

    /** 모든 오퍼레이션 공통 파라미터. serviceKey는 이미 인코딩된 값이라 build(true)로 재인코딩을 막는다. */
    private fun UriComponentsBuilder.common(): UriComponentsBuilder =
        queryParam("serviceKey", properties.serviceKey)
            .queryParam("MobileOS", properties.mobileOs)
            .queryParam("MobileApp", properties.mobileApp)
            .queryParam("_type", "json")

    private fun baseUrl(locale: DomainLocale): String = properties.baseUrls.getValue(locale.name)

    private fun codeset(locale: DomainLocale): String =
        if (locale.isKorean) TourApiProperties.CODESET_KO else TourApiProperties.CODESET_FOREIGN

    private fun categoryCode(category: SpotCategory?, locale: DomainLocale): Int? =
        category?.let { properties.categoryCodes[codeset(locale)]?.get(it.code) }

    private fun category(contentTypeId: String?, locale: DomainLocale): SpotCategory {
        val id = contentTypeId?.toIntOrNull() ?: return SpotCategory.TOURIST_SPOT
        return properties.categoryCodes[codeset(locale)]?.entries
            ?.firstOrNull { it.value == id }?.key?.let { SpotCategory(it) }
            ?: SpotCategory.TOURIST_SPOT
    }

}
