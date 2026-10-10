package org.around.ieuri.tourism.adapter.client.tourapi

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import tools.jackson.databind.annotation.JsonDeserialize
/**
 * TourAPI detailInfo2 응답 DTO. `infoname`/`infotext` 쌍의 반복 항목이다.
 * - 여행코스(25)·숙박(32)은 형태가 달라 빈 목록이 된다.
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
