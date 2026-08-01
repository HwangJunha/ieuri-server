package org.around.ieuri.tourism.infrastructure.adapter.out.tourapi

import org.around.ieuri.tourism.application.exception.SpotDetailNotFoundException
import org.around.ieuri.tourism.application.exception.UpstreamRateLimitedException
import org.around.ieuri.tourism.application.exception.UpstreamUnavailableException
import org.around.ieuri.tourism.application.port.out.client.NearbySpotData
import org.around.ieuri.tourism.application.port.out.client.NearbySpotQuery
import org.around.ieuri.tourism.application.port.out.client.NearbySpotResult
import org.around.ieuri.tourism.application.port.out.client.SpotDetailQuery
import org.around.ieuri.tourism.application.port.out.client.TourInfoPort
import org.around.ieuri.tourism.domain.aggregate.SpotDetail
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.domain.vo.SpotImage
import org.around.ieuri.tourism.infrastructure.config.TourApiProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.util.concurrent.CompletableFuture

/**
 * TourAPI locationBasedList2 어댑터. TourInfoPort의 유일한 구현체로, TourAPI 세부(필드명·코드 체계)를 여기 가둔다.
 * 도메인·application·GraphQL은 TourAPI를 모른다. **DB 적재로 전환 = 이 클래스만 교체.**
 *
 * 책임: 호출 → DTO 역직렬화 → resultCode 판정 → item을 중립 DTO(NearbySpotData)로 매핑(비관광 필터·이미지
 * https 정규화·코드→도메인 enum 매핑 포함). 거리 계산·정렬·도메인 조립은 application이 한다.
 */
@Component
class TourApiClient(
    private val properties: TourApiProperties,
    // 타임아웃 적용된 RestClient를 TourismConfig에서 빈으로 등록해 주입한다(설정 중앙화·테스트 용이).
    // RestClient의 Jackson 메시지 컨버터가 응답을 TourApiResponse로 바로 역직렬화한다(String 미경유).
    private val tourApiRestClient: RestClient,
) : TourInfoPort {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun findNearby(query: NearbySpotQuery): NearbySpotResult =
        mapResponse(call(query), query.locale)

    /**
     * 상세 조회(SPEC-0003). detailCommon2·detailImage2 정확히 2콜을 병렬 발사(동일 contentId, 상호 무관)해 지연을
     * 줄인다. common이 항목을 못 찾으면 도메인 NotFound. 이미지는 없으면 빈 갤러리(오류 아님).
     */
    override fun findDetail(query: SpotDetailQuery): SpotDetail {
        val commonFuture = CompletableFuture.supplyAsync { callDetailCommon(query) }
        val imageFuture = CompletableFuture.supplyAsync { callDetailImage(query) }
        // 두 콜을 병렬 대기. 어느 쪽이든 예외는 UpstreamException 계열로 이미 변환돼 있어 그대로 전파한다.
        val common = unwrap { commonFuture.join() }
        val images = unwrap { imageFuture.join() }
        return mapDetail(common, images, query.locale)
    }

    /** CompletableFuture.join()이 감싸는 CompletionException을 풀어 원래 도메인 예외를 노출한다. */
    private fun <T> unwrap(block: () -> T): T =
        try {
            block()
        } catch (e: java.util.concurrent.CompletionException) {
            throw e.cause ?: e
        }

    /** detailCommon2 응답 DTO → common item(없으면 NotFound). resultCode 판정 포함. */
    fun mapDetailCommon(response: TourApiDetailCommonResponse): TourApiDetailCommonResponse.Item {
        verifyResultCode(response.response.header)
        return response.response.body.items.firstOrNull()
            ?: throw SpotDetailNotFoundException()
    }

    /** detailImage2 응답 DTO → 이미지 item 목록(없으면 빈 목록). resultCode 판정 포함. */
    fun mapDetailImages(response: TourApiDetailImageResponse): List<TourApiDetailImageResponse.Item> {
        verifyResultCode(response.response.header)
        return response.response.body.items
    }

    /** 정제된 common·image를 도메인 SpotDetail로 조립(https 정규화·10장 컷·빈문자열 null화·저작권 조립). */
    fun mapDetail(
        common: TourApiDetailCommonResponse.Item,
        images: List<TourApiDetailImageResponse.Item>,
        locale: DomainLocale,
    ): SpotDetail {
        val id = common.contentid?.takeIf { it.isNotBlank() } ?: throw SpotDetailNotFoundException()
        val title = common.title?.takeIf { it.isNotBlank() } ?: throw SpotDetailNotFoundException()
        // 헤더 이미지: firstimage 없으면 갤러리 첫 장, 그것도 없으면 NotFound(썸네일 non-null 계약).
        val thumbnail = common.firstimage?.takeIf { it.isNotBlank() }?.let(::toHttps)
            ?: images.firstNotNullOfOrNull { it.originimgurl?.takeIf { u -> u.isNotBlank() }?.let(::toHttps) }
            ?: throw SpotDetailNotFoundException()

        return SpotDetail(
            id = id,
            title = title,
            category = categoryFor(common.contenttypeid, locale),
            thumbnailImageUrl = thumbnail,
            overview = common.overview.normalizeBlankToNull(),
            address = mergeAddress(common.addr1, common.addr2),
            homepage = common.homepage.normalizeBlankToNull(),
            tel = common.tel.normalizeBlankToNull(),
            copyright = CopyrightType.fromCode(common.cpyrhtDivCd),
            gallery = images.mapNotNull { it.toSpotImage() }.take(SpotDetail.MAX_GALLERY_SIZE),
        )
    }

    private fun callDetailCommon(query: SpotDetailQuery): TourApiDetailCommonResponse.Item =
        mapDetailCommon(callDetail(query, OPERATION_DETAIL_COMMON, TourApiDetailCommonResponse::class.java))

    private fun callDetailImage(query: SpotDetailQuery): List<TourApiDetailImageResponse.Item> =
        mapDetailImages(callDetail(query, OPERATION_DETAIL_IMAGE, TourApiDetailImageResponse::class.java))

    /** detailCommon2/detailImage2 공통 HTTP 호출. 네트워크/타임아웃/비2xx/파싱실패는 UpstreamUnavailable로 변환. */
    private fun <T : Any> callDetail(query: SpotDetailQuery, operation: String, type: Class<T>): T =
        try {
            tourApiRestClient.get()
                .uri(buildDetailUri(query, operation))
                .retrieve()
                .body(type)
                ?: throw UpstreamUnavailableException("TourAPI 응답 본문이 비어 있습니다.")
        } catch (e: UpstreamUnavailableException) {
            throw e
        } catch (e: SpotDetailNotFoundException) {
            throw e
        } catch (e: Exception) {
            // contentId·좌표 등 원본 식별자를 로그에 남기지 않는다(POL-013/AC-21).
            log.warn("TourAPI 상세 호출 실패: {} op={}", e.javaClass.simpleName, operation)
            throw UpstreamUnavailableException("TourAPI 호출에 실패했습니다.", e)
        }

    private fun buildDetailUri(query: SpotDetailQuery, operation: String): URI =
        UriComponentsBuilder.fromUriString(baseUrlFor(query.locale))
            .path("/$operation")
            .queryParam("serviceKey", properties.serviceKey)
            .queryParam("MobileOS", properties.mobileOs)
            .queryParam("MobileApp", properties.mobileApp)
            .queryParam("_type", "json")
            .queryParam("contentId", query.contentId)
            .build(true) // 이미 인코딩된 것으로 간주 → serviceKey 재인코딩 방지
            .toUri()

    /** resultCode 판정. 한도 초과(코드 22)는 RateLimited, 그 외 비정상은 Unavailable로 분류. */
    private fun verifyResultCode(header: TourApiResponse.Header) {
        if (header.resultCode == OK_CODE) return
        log.warn("TourAPI resultCode={}", header.resultCode)
        if (header.resultCode in RATE_LIMIT_CODES) {
            throw UpstreamRateLimitedException()
        }
        throw UpstreamUnavailableException("TourAPI 오류(resultCode=${header.resultCode}).")
    }

    /** 이미지 item → 도메인 SpotImage. 원본 url 없으면 드랍(url 없는 이미지는 모델링하지 않음). */
    private fun TourApiDetailImageResponse.Item.toSpotImage(): SpotImage? {
        val url = originimgurl?.takeIf { it.isNotBlank() }?.let(::toHttps) ?: return null
        return SpotImage(
            imageUrl = url,
            thumbnailUrl = smallimageurl?.takeIf { it.isNotBlank() }?.let(::toHttps),
            caption = imgname.normalizeBlankToNull(),
            copyright = CopyrightType.fromCode(cpyrhtDivCd),
        )
    }

    private fun String?.normalizeBlankToNull(): String? = this?.takeIf { it.isNotBlank() }

    /** TourAPI 응답 DTO → 중립 결과. resultCode 판정·필터·매핑. HTTP·역직렬화와 분리해 단위 테스트 가능. */
    fun mapResponse(response: TourApiResponse, locale: DomainLocale): NearbySpotResult {
        val header = response.response.header
        if (header.resultCode != OK_CODE) {
            log.warn("TourAPI resultCode={} msg={}", header.resultCode, header.resultMsg)
            throw UpstreamUnavailableException("TourAPI 오류(resultCode=${header.resultCode}).")
        }

        val apiBody = response.response.body
        val spots = apiBody.items.item.mapNotNull { it.toNearbySpotData(locale) }
        // 다음 upstream 페이지 존재: pageNo*numOfRows < totalCount.
        val hasMore = apiBody.pageNo.toLong() * apiBody.numOfRows.toLong() < apiBody.totalCount.toLong()

        return NearbySpotResult(spots = spots, totalCount = apiBody.totalCount, hasMoreUpstreamPages = hasMore)
    }

    /** HTTP 호출 + Jackson 역직렬화(RestClient 컨버터). 네트워크/타임아웃/비2xx/파싱실패는 UpstreamUnavailable로 변환. */
    private fun call(query: NearbySpotQuery): TourApiResponse =
        try {
            tourApiRestClient.get()
                .uri(buildUri(query))
                .retrieve()
                .body(TourApiResponse::class.java)
                ?: throw UpstreamUnavailableException("TourAPI 응답 본문이 비어 있습니다.")
        } catch (e: UpstreamUnavailableException) {
            throw e
        } catch (e: Exception) {
            // 좌표를 로그에 남기지 않는다(POL-013/AC-23).
            log.warn("Error while calling tour api", e)
            log.warn("TourAPI 호출 실패: {}", e.javaClass.simpleName)
            throw UpstreamUnavailableException("TourAPI 호출에 실패했습니다.", e)
        }

    /**
     * 요청 URI. serviceKey는 이미 인코딩된 상태일 수 있어 재인코딩을 막아야 한다(함정 1) → build(true)로 이미
     * 인코딩된 것으로 간주하고, 나머지 파라미터는 특수문자가 없는 숫자·enum·고정문자열이라 안전하다.
     */
    private fun buildUri(query: NearbySpotQuery): URI =
        UriComponentsBuilder.fromUriString(baseUrlFor(query.locale))
            .path("/$OPERATION_LOCATION_BASED")
            .queryParam("serviceKey", properties.serviceKey)
            .queryParam("MobileOS", properties.mobileOs)
            .queryParam("MobileApp", properties.mobileApp)
            .queryParam("_type", "json")
            .queryParam("mapX", query.center.longitude)
            .queryParam("mapY", query.center.latitude)
            .queryParam("radius", query.radiusMeters)
            .queryParam("numOfRows", query.numOfRows)
            .queryParam("pageNo", query.pageNo)
            .queryParam("arrange", properties.arrange)
            .apply { contentTypeIdFor(query.category, query.locale)?.let { queryParam("contentTypeId", it) } }
            .build(true) // 이미 인코딩된 것으로 간주 → serviceKey 재인코딩 방지
            .toUri()

    private fun baseUrlFor(locale: DomainLocale): String =
        properties.baseUrls[locale.name]
            ?: throw UpstreamUnavailableException("지원하지 않는 언어 설정입니다: ${locale.name}")

    /** 도메인 카테고리 → 언어별 TourAPI contentTypeId. category 없으면 null(전체). 코드 체계를 어댑터에 가둔다. */
    private fun contentTypeIdFor(category: SpotCategory?, locale: DomainLocale): Int? {
        if (category == null) return null
        return properties.categoryCodes[codesetFor(locale)]?.get(category)
    }

    /** 응답 contenttypeid → 도메인 카테고리 역매핑(설정 역방향). 실패 시 TOURIST_SPOT. */
    private fun categoryFor(contentTypeId: String?, locale: DomainLocale): SpotCategory {
        val id = contentTypeId?.toIntOrNull() ?: return SpotCategory.TOURIST_SPOT
        return properties.categoryCodes[codesetFor(locale)]
            ?.entries?.firstOrNull { it.value == id }?.key
            ?: SpotCategory.TOURIST_SPOT
    }

    private fun codesetFor(locale: DomainLocale): String =
        if (locale.isKorean) TourApiProperties.CODESET_KO else TourApiProperties.CODESET_FOREIGN

    /** TourAPI item → 중립 DTO. 비관광·이미지 없음 항목은 null(드랍). */
    private fun TourApiResponse.Item.toNearbySpotData(locale: DomainLocale): NearbySpotData? {
        // 비관광 필터(lclsSystm2 제외 코드).
        if (lclsSystm2 != null && lclsSystm2 in properties.excludeLclssystm2) return null
        // arrange=Q 전제상 이미지·좌표·필수 필드는 보장되나, 빈 이미지만 방어적으로 드랍.
        val image = firstimage?.takeIf { it.isNotBlank() }?.let(::toHttps) ?: return null
        val id = contentid ?: return null
        val name = title ?: return null
        val x = mapx ?: return null
        val y = mapy ?: return null

        return NearbySpotData(
            id = id,
            title = name,
            address = mergeAddress(addr1, addr2),
            thumbnailImageUrl = image,
            coordinates = Coordinates(latitude = y, longitude = x),
            category = categoryFor(contenttypeid, locale),
            copyright = CopyrightType.fromCode(cpyrhtDivCd),
        )
    }

    private fun toHttps(url: String): String =
        if (url.startsWith("http://")) "https://" + url.removePrefix("http://") else url

    private fun mergeAddress(addr1: String?, addr2: String?): String? =
        listOfNotNull(addr1?.takeIf { it.isNotBlank() }, addr2?.takeIf { it.isNotBlank() })
            .joinToString(" ")
            .ifBlank { null }

    private companion object {
        const val OK_CODE = "0000"
        const val OPERATION_LOCATION_BASED = "locationBasedList2"
        const val OPERATION_DETAIL_COMMON = "detailCommon2"
        const val OPERATION_DETAIL_IMAGE = "detailImage2"
        // TourAPI(data.go.kr 게이트웨이) 한도 초과 코드. 22=일일 트래픽 초과, 05=서비스 요청 한도 초과.
        val RATE_LIMIT_CODES = setOf("22", "05")
    }
}
