package org.around.ieuri.tourism.infrastructure.adapter.out.client.tourapi

import org.around.ieuri.tourism.application.exception.UpstreamRateLimitedException
import org.around.ieuri.tourism.application.exception.UpstreamTooManyRequestsException
import org.around.ieuri.tourism.application.exception.UpstreamUnavailableException
import org.around.ieuri.tourism.application.port.out.client.NearbySpotQuery
import org.around.ieuri.tourism.application.port.out.client.NearbySpotResult
import org.around.ieuri.tourism.application.port.out.client.SpotDetailQuery
import org.around.ieuri.tourism.application.port.out.client.TourInfoPort
import org.around.ieuri.tourism.domain.aggregate.SpotDetail
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.application.port.out.query.CategoryExternalMappingPort
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
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
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
    private val categoryMapping: CategoryExternalMappingPort,
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
        val response: TourApiDetailCommonResponse = call(detailUri(query, "detailCommon2"), TourApiDetailCommonResponse::class.java)
        verify(response.response.header)
        val item = response.response.body.items.firstOrNull() ?: return null
        return mapDetail(item, query.locale)
    }

    /** 갤러리(detailImage2 1콜). */
    override fun findGallery(query: SpotDetailQuery): List<SpotImage> {
        val response: TourApiDetailImageResponse = call(detailUri(query, "detailImage2"), TourApiDetailImageResponse::class.java)
        verify(response.response.header)
        return mapGallery(response.response.body.items)
    }

    /** 운영 정보(detailIntro2 1콜). 언어별 카테고리 코드가 없으면 호출 자체를 생략한다. */
    override fun findIntro(query: SpotDetailQuery, category: SpotCategory): SpotIntro? {
        val contentTypeId = categoryCode(category, query.locale) ?: return null
        val response: TourApiDetailIntroResponse = call(detailUri(query, "detailIntro2", contentTypeId), TourApiDetailIntroResponse::class.java)
        verify(response.response.header)
        return mapIntro(response.response.body.items.firstOrNull())
    }

    /** 이용안내 항목(detailInfo2 1콜). 언어별 카테고리 코드가 없으면 호출 자체를 생략한다. */
    override fun findInfoItems(query: SpotDetailQuery, category: SpotCategory): List<SpotInfoItem> {
        val contentTypeId = categoryCode(category, query.locale) ?: return emptyList()
        val response: TourApiDetailInfoResponse = call(detailUri(query, "detailInfo2", contentTypeId), TourApiDetailInfoResponse::class.java)
        verify(response.response.header)
        return mapInfoItems(response.response.body.items)
    }

    /**
     * 상세 오퍼레이션 호출 + **전송 계층 예외의 도메인 변환**(SPEC-0037). 연결 실패·타임아웃·
     * 5xx·역직렬화 실패가 그대로 새면 재시도 대상인지 판단할 수 없어, FR-008이 지정한
     * "네트워크 오류는 재시도한다"가 성립하지 않는다.
     *
     * HTTP 429만 초당 한도로 가르고 나머지는 전부 장애다 — 4xx도 포함되는 것은 배치 관점에서
     * 결론(넘어간다)이 같기 때문이다. **URI는 로그에 싣지 않는다**(serviceKey 누출).
     */
    private fun <T : Any> call(uri: URI, type: Class<T>): T =
        try {
            tourApiRestClient.get().uri(uri).retrieve().body(type)
                ?: throw UpstreamUnavailableException("TourAPI 응답이 비어 있습니다.")
        } catch (e: RestClientException) {
            throw translate(e)
        }

    fun translate(e: RestClientException): RuntimeException {
        val status = (e as? RestClientResponseException)?.statusCode?.value()
        if (status == HTTP_TOO_MANY_REQUESTS) {
            return UpstreamTooManyRequestsException("TourAPI 초당 호출 한도를 초과했습니다.", e)
        }
        log.warn(
            "TourAPI 호출 실패: {} status={} cause={}",
            e.javaClass.simpleName, status, e.cause?.javaClass?.simpleName ?: "-",
        )
        return UpstreamUnavailableException("TourAPI 호출에 실패했습니다(${e.javaClass.simpleName}).", e)
    }

    /**
     * 공공데이터포털 응답 코드 판정(SPEC-0037). **호출자가 구분해야 하는 세 결론만** 가른다 —
     * 오늘은 그만둔다(일일 한도 `22`) · 잠시 뒤 다시 한다(초당 한도 `23`) · 넘어간다(그 밖의 전부).
     * 나머지 코드는 배치에서 결론이 같으므로 타입을 나누지 않는다.
     *
     * **빈 코드는 성공으로 본다** — DTO 기본값이 빈 문자열이라, 헤더 없는 응답을 실패로 만들면
     * 정상 조회가 장애로 둔갑한다(`TourStatsClient`와 같은 규율). 결과 없음은 items가 비어 오는
     * 것으로 이미 표현되며 여기서 예외가 되지 않는다(SPEC-0008 R-1).
     *
     * **resultMsg는 로그에 싣지 않는다** — 오류 메시지에 요청 URI가 섞여 오면 serviceKey가 샌다.
     *
     * HTTP·역직렬화와 분리돼 있어 단위 테스트가 가능하다([translate]도 같다).
     */
    fun verify(header: TourApiResponse.Header) {
        val code = header.resultCode
        when {
            code.isBlank() || code == RESULT_OK -> return
            code == RESULT_DAILY_LIMIT -> throw UpstreamRateLimitedException("TourAPI 일일 호출 한도를 초과했습니다.")
            code == RESULT_PER_SECOND_LIMIT -> throw UpstreamTooManyRequestsException("TourAPI 초당 호출 한도를 초과했습니다.")
            else -> {
                log.warn("TourAPI 오류 응답: code={}", code)
                throw UpstreamUnavailableException("TourAPI 오류 응답: $code")
            }
        }
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
    private fun detailUri(query: SpotDetailQuery, operation: String, contentTypeId: String? = null): URI =
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

    /**
     * 이 카테고리의 contentTypeId. **없으면 null이며 호출자가 upstream 호출을 생략한다** —
     * 그 코드셋에 대응 코드가 없는 카테고리가 실제로 있다(여행코스는 외국어, 교통은 국문에 없다).
     */
    private fun categoryCode(category: SpotCategory?, locale: DomainLocale): String? =
        category?.let {
            categoryMapping.externalCode(it, CategoryExternalMappingPort.PROVIDER_TOUR_API, codeset(locale))
        }

    /** contentTypeId가 가리키는 우리 카테고리. 모르는 코드는 관광지로 떨어뜨린다(기존 동작). */
    private fun category(contentTypeId: String?, locale: DomainLocale): SpotCategory =
        contentTypeId?.let {
            categoryMapping.categoryOf(it, CategoryExternalMappingPort.PROVIDER_TOUR_API, codeset(locale))
        } ?: SpotCategory.TOURIST_SPOT

    companion object {
        private val log = LoggerFactory.getLogger(TourApiClient::class.java)

        private const val RESULT_OK = "0000"

        /** `LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR` — 그날이 끝났다. */
        private const val RESULT_DAILY_LIMIT = "22"

        /** `LIMITED_NUMBER_OF_SERVICE_REQUESTS_PER_SECOND_EXCEEDS_ERROR` — 잠시 뒤 풀린다. */
        private const val RESULT_PER_SECOND_LIMIT = "23"

        private const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
