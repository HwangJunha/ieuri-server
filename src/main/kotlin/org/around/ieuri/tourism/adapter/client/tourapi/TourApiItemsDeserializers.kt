package org.around.ieuri.tourism.adapter.client.tourapi

import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ValueDeserializer
/** TourAPI `items` 필드 정규화 — 문자열(빈 문자열 등)은 빈 리스트, 객체 단건은 1개 리스트. */
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
