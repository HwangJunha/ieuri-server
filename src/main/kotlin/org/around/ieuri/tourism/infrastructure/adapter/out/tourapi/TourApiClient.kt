package org.around.ieuri.tourism.infrastructure.adapter.out.tourapi

import org.around.ieuri.tourism.application.exception.UpstreamUnavailableException
import org.around.ieuri.tourism.application.port.out.client.NearbySpotQuery
import org.around.ieuri.tourism.application.port.out.client.NearbySpotResult
import org.around.ieuri.tourism.application.port.out.client.TourInfoPort
import org.around.ieuri.tourism.domain.aggregate.Spot
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.Distance
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.infrastructure.config.TourApiProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.net.URI

/**
 * TourAPI locationBasedList2 어댑터. TourInfoPort의 유일한 구현체로, TourAPI 세부(필드명·코드·함정)를 여기 가둔다.
 * 도메인·application·GraphQL은 TourAPI를 모른다. **DB 적재로 전환 = 이 클래스만 교체.**
 *
 * 어댑터 전담 함정 5종(§4.8, AC-7, 누출 금지):
 *  1. serviceKey '+' 이중 인코딩 → raw 쿼리스트링을 직접 조립하고 RestClient가 재인코딩하지 않게 URI.create로 넘긴다.
 *  2. JSON 요청에도 오류는 XML → 본문/Content-Type 스니핑으로 XML/오류 resultCode를 UpstreamUnavailableException으로 변환.
 *  3. 0건 시 items가 빈 문자열 → 빈 리스트로 정규화.
 *  4. 1건 시 item이 객체 → 단일 원소 리스트로 정규화.
 *  5. 미정의 필드 → JsonNode로 필드명 조회(관대한 역직렬화, 알 수 없는 필드 무시).
 *
 * 또한 비관광 필터(lclsSystm2), 이미지 http→https 정규화, 이미지 없는 항목 드랍, 언어→contentTypeId 매핑,
 * 거리 계산(도메인 Distance)을 여기서 끝내 도메인 Spot을 반환한다.
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
        parseResponse(call(query), query)

    /**
     * 응답 본문(String) → 도메인 결과. HTTP와 분리해 함정 처리·매핑을 단위 테스트 가능하게 한다(internal).
     * 함정 2/3/4/5와 필터·정규화·거리 계산을 여기서 끝낸다.
     */
    internal fun parseResponse(body: String, query: NearbySpotQuery): NearbySpotResult {
        val root = parseJsonOrUpstreamError(body)
        assertResultOk(root)

        val bodyNode = root.path("response").path("body")
        val totalCount = bodyNode.path("totalCount").asIntOrNull()
        val numOfRows = bodyNode.path("numOfRows").asIntOrNull() ?: properties.upstreamPageSize
        val pageNo = bodyNode.path("pageNo").asIntOrNull() ?: query.pageNo

        val items = normalizeItems(bodyNode.path("items"))
        val spots = items.mapNotNull { it.toSpotOrNull(query) }

        // upstream 다음 페이지 존재: pageNo*numOfRows < totalCount (totalCount 있을 때).
        val hasMore = totalCount?.let { pageNo.toLong() * numOfRows.toLong() < it.toLong() } ?: items.isNotEmpty()

        return NearbySpotResult(spots = spots, totalCount = totalCount, hasMoreUpstreamPages = hasMore)
    }

    /** HTTP 호출. 네트워크/타임아웃/비2xx는 UpstreamUnavailableException으로 변환(누출 금지). */
    private fun call(query: NearbySpotQuery): String {
        val uri = buildUri(query)
        return try {
            tourApiRestClient.get()
                .uri(uri)
                .retrieve()
                .body(String::class.java)
                ?: throw UpstreamUnavailableException("TourAPI 응답 본문이 비어 있습니다.")
        } catch (e: UpstreamUnavailableException) {
            throw e
        } catch (e: Exception) {
            // 좌표를 로그에 남기지 않는다(POL-013/AC-23) — 예외 메시지에 URI(좌표 포함)를 넣지 않는다.
            log.warn("TourAPI 호출 실패: {}", e.javaClass.simpleName)
            throw UpstreamUnavailableException("TourAPI 호출에 실패했습니다.", e)
        }
    }

    /**
     * raw 쿼리스트링을 직접 조립한다(함정 1). serviceKey는 이미 인코딩된 상태일 수 있어 재인코딩하지 않고,
     * 나머지 값은 최소 인코딩만 적용한다. 좌표는 숫자라 인코딩 불필요.
     */
    private fun buildUri(query: NearbySpotQuery): URI {
        val baseUrl = baseUrlFor(query.locale)
        val params = buildList {
            add("serviceKey=${properties.serviceKey}") // 사전 인코딩 키 재인코딩 금지
            add("MobileOS=${properties.mobileOs}")
            add("MobileApp=${properties.mobileApp}")
            add("_type=json")
            add("mapX=${query.center.longitude}")
            add("mapY=${query.center.latitude}")
            add("radius=${query.radiusMeters}")
            add("numOfRows=${query.numOfRows}")
            add("pageNo=${query.pageNo}")
            add("arrange=${properties.arrange}")
            contentTypeIdFor(query.category, query.locale)?.let { add("contentTypeId=$it") }
        }
        return URI.create("$baseUrl/locationBasedList2?${params.joinToString("&")}")
    }

    private fun baseUrlFor(locale: DomainLocale): String =
        properties.baseUrls[locale.name]
            ?: throw UpstreamUnavailableException("지원하지 않는 언어 설정입니다: ${locale.name}")

    /** 언어→코드셋(KO/FOREIGN) 선택 후 도메인 카테고리→contentTypeId. category 없으면 null(전체). */
    private fun contentTypeIdFor(category: SpotCategory?, locale: DomainLocale): Int? {
        if (category == null) return null
        val codeset = if (locale.isKorean) TourApiProperties.CODESET_KO else TourApiProperties.CODESET_FOREIGN
        return properties.categoryCodes[codeset]?.get(category)
    }

    private fun parseJsonOrUpstreamError(body: String): JsonNode {
        val trimmed = body.trimStart()
        // 함정 2: JSON 요청에도 오류는 XML로 온다. XML/HTML이면 upstream 오류로 변환.
        if (trimmed.startsWith("<")) {
            log.warn("TourAPI가 비-JSON(XML 추정) 오류 응답을 반환했습니다.")
            throw UpstreamUnavailableException("TourAPI 오류 응답(XML).")
        }
        return try {
            objectMapper.readTree(body)
        } catch (e: Exception) {
            log.warn("TourAPI 응답 파싱 실패: {}", e.javaClass.simpleName)
            throw UpstreamUnavailableException("TourAPI 응답을 해석할 수 없습니다.", e)
        }
    }

    /** resultCode가 0000이 아니면 upstream 오류. */
    private fun assertResultOk(root: JsonNode) {
        val header = root.path("response").path("header")
        val code = header.path("resultCode").asStringOrNull()
        if (code != null && code != OK_CODE) {
            val msg = header.path("resultMsg").asStringOrNull() ?: "unknown"
            log.warn("TourAPI resultCode={} msg={}", code, msg)
            throw UpstreamUnavailableException("TourAPI 오류(resultCode=$code).")
        }
    }

    /**
     * items 정규화(함정 3·4). items가 빈 문자열/누락이면 빈 리스트, item이 객체면 단일 원소 리스트.
     */
    private fun normalizeItems(itemsNode: JsonNode): List<JsonNode> {
        if (itemsNode.isMissingNode || itemsNode.isNull) return emptyList()
        // 함정 3: 0건이면 "items": "" (문자열).
        if (itemsNode.isString) return emptyList()
        val itemNode = itemsNode.path("item")
        return when {
            itemNode.isMissingNode || itemNode.isNull -> emptyList()
            itemNode.isString -> emptyList() // item도 빈 문자열로 오는 변종 방어
            itemNode.isArray -> itemNode.toList()
            itemNode.isObject -> listOf(itemNode) // 함정 4: 1건이면 객체
            else -> emptyList()
        }
    }

    /** TourAPI item → 도메인 Spot. 비관광·이미지 없음·좌표 결손 항목은 null(드랍). */
    private fun JsonNode.toSpotOrNull(query: NearbySpotQuery): Spot? {
        val contentId = path("contentid").asStringOrNull() ?: return null

        // 비관광 필터(lclsSystm2 제외 코드).
        val lcls2 = path("lclsSystm2").asStringOrNull()
        if (lcls2 != null && lcls2 in properties.excludeLclssystm2) return null

        // 이미지 https 정규화 + 없는 항목 드랍(방어적).
        val image = path("firstimage").asStringOrNull()?.let(::toHttps)
        if (image.isNullOrBlank()) return null

        val mapX = path("mapx").asDoubleOrNull() ?: return null // 경도
        val mapY = path("mapy").asDoubleOrNull() ?: return null // 위도
        val coordinates = try {
            Coordinates(latitude = mapY, longitude = mapX)
        } catch (e: IllegalArgumentException) {
            return null // 좌표 범위 이상치 드랍
        }

        val title = path("title").asStringOrNull() ?: return null
        val address = mergeAddress(path("addr1").asStringOrNull(), path("addr2").asStringOrNull())
        val copyright = mapCopyright(path("cpyrhtDivCd").asStringOrNull())

        return Spot(
            id = contentId,
            title = title,
            address = address,
            thumbnailImageUrl = image,
            coordinates = coordinates,
            distance = Distance.between(query.center, coordinates),
            category = query.category ?: mapCategoryOrDefault(path("contenttypeid").asStringOrNull(), query.locale),
            copyright = copyright,
            attributionText = attributionTextFor(copyright),
        )
    }

    private fun toHttps(url: String): String =
        if (url.startsWith("http://")) "https://" + url.removePrefix("http://") else url

    private fun mergeAddress(addr1: String?, addr2: String?): String? {
        val a1 = addr1?.takeIf { it.isNotBlank() }
        val a2 = addr2?.takeIf { it.isNotBlank() }
        return listOfNotNull(a1, a2).joinToString(" ").ifBlank { null }
    }

    private fun mapCopyright(raw: String?): CopyrightType = when (raw?.trim()?.lowercase()) {
        "type1" -> CopyrightType.TYPE1
        "type3" -> CopyrightType.TYPE3
        else -> CopyrightType.UNSPECIFIED
    }

    /**
     * category 인자가 없을 때 응답의 contenttypeid로 도메인 카테고리를 역매핑. 실패 시 TOURIST_SPOT.
     * (설정의 코드셋을 역방향으로 조회.)
     */
    private fun mapCategoryOrDefault(contentTypeId: String?, locale: DomainLocale): SpotCategory {
        val id = contentTypeId?.toIntOrNull() ?: return SpotCategory.TOURIST_SPOT
        val codeset = if (locale.isKorean) TourApiProperties.CODESET_KO else TourApiProperties.CODESET_FOREIGN
        return properties.categoryCodes[codeset]
            ?.entries?.firstOrNull { it.value == id }?.key
            ?: SpotCategory.TOURIST_SPOT
    }

    /** 최소 출처 문구(AC-15). cpyrhtDivCd가 빈 값이어도 placeholder가 아니라 실제 출처를 담는다. */
    private fun attributionTextFor(copyright: CopyrightType): String = when (copyright) {
        CopyrightType.TYPE1 -> "출처: 한국관광공사(공공누리 제1유형)"
        CopyrightType.TYPE3 -> "출처: 한국관광공사(공공누리 제3유형·변경금지)"
        CopyrightType.UNSPECIFIED -> "출처: 한국관광공사"
    }

    // --- JsonNode 관대한 접근 헬퍼(누락/타입불일치/비스칼라 시 null) ---
    private fun JsonNode.asStringOrNull(): String? =
        if (isValueNode && !isNull) asString().takeIf { it.isNotBlank() } else null

    private fun JsonNode.asIntOrNull(): Int? =
        if (isValueNode && !isNull) asString().trim().toIntOrNull() else null

    private fun JsonNode.asDoubleOrNull(): Double? =
        if (isValueNode && !isNull) asString().trim().toDoubleOrNull() else null

    private companion object {
        const val OK_CODE = "0000"
    }
}
