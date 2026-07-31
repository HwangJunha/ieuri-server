package org.around.ieuri.tourism.infrastructure.adapter.out.tourapi

import org.around.ieuri.tourism.application.exception.UpstreamUnavailableException
import org.around.ieuri.tourism.application.port.out.client.NearbySpotData
import org.around.ieuri.tourism.application.port.out.client.NearbySpotQuery
import org.around.ieuri.tourism.application.port.out.client.NearbySpotResult
import org.around.ieuri.tourism.application.port.out.client.TourInfoPort
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.infrastructure.config.TourApiProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import tools.jackson.databind.ObjectMapper
import java.net.URI

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
    private val objectMapper: ObjectMapper,
    // 타임아웃 적용된 RestClient를 TourismConfig에서 빈으로 등록해 주입한다(설정 중앙화·테스트 용이).
    private val tourApiRestClient: RestClient,
) : TourInfoPort {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun findNearby(query: NearbySpotQuery): NearbySpotResult =
        parseAndMap(call(query), query.locale)

    /** 응답 본문(String) → 중립 결과. HTTP와 분리해 역직렬화·resultCode 판정·매핑을 단위 테스트 가능하게 한다. */
    fun parseAndMap(body: String, locale: DomainLocale): NearbySpotResult {
        val response = try {
            objectMapper.readValue(body, TourApiResponse::class.java)
        } catch (e: Exception) {
            log.warn("TourAPI 응답 파싱 실패: {}", e.javaClass.simpleName)
            throw UpstreamUnavailableException("TourAPI 응답을 해석할 수 없습니다.", e)
        }

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

    /** HTTP 호출. 네트워크/타임아웃/비2xx는 UpstreamUnavailableException으로 변환(누출 금지). */
    private fun call(query: NearbySpotQuery): String =
        try {
            tourApiRestClient.get()
                .uri(buildUri(query))
                .retrieve()
                .body(String::class.java)
                ?: throw UpstreamUnavailableException("TourAPI 응답 본문이 비어 있습니다.")
        } catch (e: UpstreamUnavailableException) {
            throw e
        } catch (e: Exception) {
            // 좌표를 로그에 남기지 않는다(POL-013/AC-23).
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
    }
}
