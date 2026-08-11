package org.around.ieuri.tourism.infrastructure.adapter.out.client.tourapi

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.annotation.JsonDeserialize

/**
 * TourAPI detailCommon2 응답 DTO. **어댑터 내부 전용** — TourAPI 필드명을 그대로 담으며 어댑터 밖으로 나가지 않는다.
 *
 * items 함정 2종을 [ItemsDeserializer]가 흡수한다:
 *  - 결과 없음 → `"items": ""`(빈 문자열) → item 빈 리스트.
 *  - 단건 → `"items": { "item": { ... } }`(객체) → 1개 리스트로 정규화(AC-10 계열).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class TourApiDetailCommonResponse(
    val response: Response = Response(),
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Response(
        val header: TourApiResponse.Header = TourApiResponse.Header(),
        val body: Body = Body(),
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Body(
        @param:JsonDeserialize(using = CommonItemsDeserializer::class)
        val items: List<Item> = emptyList(),
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Item(
        val contentid: String? = null,
        val title: String? = null,
        val addr1: String? = null,
        val addr2: String? = null,
        val overview: String? = null,
        val homepage: String? = null,
        val tel: String? = null,
        val firstimage: String? = null,
        val contenttypeid: String? = null,
        val cpyrhtDivCd: String? = null,
    )
}

/**
 * TourAPI detailImage2 응답 DTO. 이미지별 cpyrhtDivCd(저작권 유형)를 담는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class TourApiDetailImageResponse(
    val response: Response = Response(),
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Response(
        val header: TourApiResponse.Header = TourApiResponse.Header(),
        val body: Body = Body(),
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Body(
        @param:JsonDeserialize(using = ImageItemsDeserializer::class)
        val items: List<Item> = emptyList(),
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Item(
        val originimgurl: String? = null,
        val smallimageurl: String? = null,
        val imgname: String? = null,
        val cpyrhtDivCd: String? = null,
    )
}

/**
 * TourAPI detailIntro2 응답 DTO. **카테고리(contentTypeId)마다 필드가 완전히 다르다** — 관광지는 `usetime`,
 * 음식점은 `opentimefood`, 문화시설은 `usetimeculture` … 그래서 한 Item에 카테고리별 변형을 모두 nullable로
 * 담고, 어댑터가 의미가 같은 것끼리 공통 도메인 필드로 정규화한다(응답은 그중 자기 카테고리 필드만 채워 준다).
 *
 * 여기 없는 필드는 무시된다(@JsonIgnoreProperties) — 카테고리 고유 필드까지 전부 담지는 않는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class TourApiDetailIntroResponse(
    val response: Response = Response(),
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Response(
        val header: TourApiResponse.Header = TourApiResponse.Header(),
        val body: Body = Body(),
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Body(
        @param:JsonDeserialize(using = IntroItemsDeserializer::class)
        val items: List<Item> = emptyList(),
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Item(
        // 이용/영업 시간
        val usetime: String? = null,          // 12 관광지
        val usetimeculture: String? = null,   // 14 문화시설
        val usetimefestival: String? = null,  // 15 축제
        val usetimeleports: String? = null,   // 28 레포츠
        val opentime: String? = null,         // 38 쇼핑 (주의: opentimeshopping 아님)
        val opentimefood: String? = null,     // 39 음식점
        // 휴무일
        val restdate: String? = null,
        val restdateculture: String? = null,
        val restdateleports: String? = null,
        val restdateshopping: String? = null,
        val restdatefood: String? = null,
        // 주차
        val parking: String? = null,
        val parkingculture: String? = null,
        val parkingleports: String? = null,
        val parkinglodging: String? = null,
        val parkingshopping: String? = null,
        val parkingfood: String? = null,
        // 문의·안내
        val infocenter: String? = null,
        val infocenterculture: String? = null,
        val infocenterleports: String? = null,
        val infocenterlodging: String? = null,
        val infocentershopping: String? = null,
        val infocenterfood: String? = null,
        val infocentertourcourse: String? = null, // 25 여행코스
        // 요금
        val usefee: String? = null,           // 14 문화시설
        val usefeeleports: String? = null,    // 28 레포츠
        // 반려동물
        val chkpet: String? = null,
        val chkpetculture: String? = null,
        val chkpetleports: String? = null,
        val chkpetshopping: String? = null,
        // 신용카드
        val chkcreditcard: String? = null,
        val chkcreditcardculture: String? = null,
        val chkcreditcardleports: String? = null,
        val chkcreditcardshopping: String? = null,
        val chkcreditcardfood: String? = null,
    )
}

/**
 * TourAPI detailInfo2 응답 DTO. 반복 항목(0..N)이며 기본 형태는 `infoname`/`infotext` 쌍이다.
 * 여행코스(25)·숙박(32)은 형태가 전혀 다르지만(코스 일정·객실 정보) 이번 범위는 공통 형태만 다룬다 —
 * 그 경우 infoname/infotext가 없어 빈 목록이 된다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class TourApiDetailInfoResponse(
    val response: Response = Response(),
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Response(
        val header: TourApiResponse.Header = TourApiResponse.Header(),
        val body: Body = Body(),
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Body(
        @param:JsonDeserialize(using = InfoItemsDeserializer::class)
        val items: List<Item> = emptyList(),
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Item(
        val infoname: String? = null,
        val infotext: String? = null,
        val serialnum: String? = null,
    )
}

/**
 * TourAPI `items` 필드 함정을 흡수하는 공통 역직렬화 로직.
 *  - 문자열(빈 문자열 등) → 빈 리스트.
 *  - `item`이 배열이면 그대로, 객체 단건이면 1개 리스트로.
 */
private inline fun <reified T> readItems(parser: JsonParser, context: DeserializationContext): List<T> {
    val node: JsonNode = parser.readValueAsTree()
    // 결과 없음: TourAPI가 items를 빈 문자열로 준다.
    if (!node.isObject) return emptyList()
    val itemNode = node.get("item") ?: return emptyList()
    return when {
        itemNode.isArray -> itemNode.map<JsonNode, T> { context.readTreeAsValue(it, T::class.java) }
        itemNode.isObject -> listOf<T>(context.readTreeAsValue(itemNode, T::class.java))
        else -> emptyList()
    }
}

class CommonItemsDeserializer : ValueDeserializer<List<TourApiDetailCommonResponse.Item>>() {
    override fun deserialize(
        parser: JsonParser,
        context: DeserializationContext,
    ): List<TourApiDetailCommonResponse.Item> = readItems(parser, context)
}

class ImageItemsDeserializer : ValueDeserializer<List<TourApiDetailImageResponse.Item>>() {
    override fun deserialize(
        parser: JsonParser,
        context: DeserializationContext,
    ): List<TourApiDetailImageResponse.Item> = readItems(parser, context)
}

class IntroItemsDeserializer : ValueDeserializer<List<TourApiDetailIntroResponse.Item>>() {
    override fun deserialize(
        parser: JsonParser,
        context: DeserializationContext,
    ): List<TourApiDetailIntroResponse.Item> = readItems(parser, context)
}

class InfoItemsDeserializer : ValueDeserializer<List<TourApiDetailInfoResponse.Item>>() {
    override fun deserialize(
        parser: JsonParser,
        context: DeserializationContext,
    ): List<TourApiDetailInfoResponse.Item> = readItems(parser, context)
}
