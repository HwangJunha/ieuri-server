package org.around.ieuri.tourism.infrastructure.adapter.out.client.tourapi

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * TourAPI locationBasedList2 응답 DTO. **어댑터 내부 전용** — TourAPI 필드명을 그대로 담으며 어댑터 밖으로 나가지 않는다.
 * 미정의 필드는 무시(@JsonIgnoreProperties)해 관대하게 역직렬화한다. 실측상 items.item은 배열로 일관되게 온다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class TourApiResponse(
    val response: Response = Response(),
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Response(
        val header: Header = Header(),
        val body: Body = Body(),
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Header(
        val resultCode: String = "",
        val resultMsg: String = "",
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Body(
        val totalCount: Int = 0,
        val numOfRows: Int = 0,
        val pageNo: Int = 0,
        val items: Items = Items(),
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Items(
        val item: List<Item> = emptyList(),
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Item(
        val contentid: String? = null,
        val title: String? = null,
        val addr1: String? = null,
        val addr2: String? = null,
        val firstimage: String? = null,
        val mapx: Double? = null,
        val mapy: Double? = null,
        val contenttypeid: String? = null,
        val lclsSystm2: String? = null,
        val cpyrhtDivCd: String? = null,
        /**
         * 표출 여부. areaBasedSyncList2에만 있고 locationBasedList2 응답에는 없다.
         * `"1"`이 아닌 값과 결측은 모두 적재 대상이 아니다 — 결측을 `"1"`로 간주하면
         * 표출이 중단된 항목이 그대로 들어온다(SPEC-0029 R-2).
         */
        val showflag: String? = null,
    )
}
