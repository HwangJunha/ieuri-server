package org.around.ieuri.tourism.adapter.client.tourapi

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import tools.jackson.databind.annotation.JsonDeserialize
/**
 * TourAPI detailCommon2 응답 DTO(어댑터 내부 전용).
 * - 결과 없음(`"items": ""`)은 빈 리스트, 단건 객체는 1개 리스트로 정규화한다([ItemsDeserializer]).
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
