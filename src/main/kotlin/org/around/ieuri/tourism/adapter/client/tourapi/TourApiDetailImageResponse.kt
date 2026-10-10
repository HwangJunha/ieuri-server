package org.around.ieuri.tourism.adapter.client.tourapi

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import tools.jackson.databind.annotation.JsonDeserialize
/** TourAPI detailImage2 응답 DTO. 이미지별 cpyrhtDivCd(저작권 유형)를 담는다. */
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
