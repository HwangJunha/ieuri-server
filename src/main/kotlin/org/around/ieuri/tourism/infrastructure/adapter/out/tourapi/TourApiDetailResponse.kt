package org.around.ieuri.tourism.infrastructure.adapter.out.tourapi

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
