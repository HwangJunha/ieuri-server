package org.around.ieuri.tourism.adapter.client.tourapi

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/** TourAPI searchFestival2 응답 DTO(어댑터 내부 전용). 미정의 필드는 무시한다. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class TourApiFestivalResponse(
    val response: Response = Response(),
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Response(
        val body: Body = Body(),
    )

    /** `header`·`numOfRows`·`pageNo`는 읽는 코드가 없어 선언하지 않는다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Body(
        val totalCount: Int = 0,
        val items: Items = Items(),
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Items(
        val item: List<Item> = emptyList(),
    )

    /**
     * 축제 1건. 적재에 필요한 것만 선언한다(제목·주소·좌표는 `spot` 적재가 맡는다, AC-3).
     * - 날짜는 `YYYYMMDD` 문자열이다. `progresstype`/`festivaltype`은 해석하지 않고 원문 그대로 둔다(R-5).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Item(
        val contentid: String? = null,
        val eventstartdate: String? = null,
        val eventenddate: String? = null,
        val progresstype: String? = null,
        val festivaltype: String? = null,
    )
}
