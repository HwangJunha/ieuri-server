package org.around.ieuri.tourism.infrastructure.adapter.out.tourapi

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * TourAPI searchFestival2 응답 DTO. **어댑터 내부 전용** — TourAPI 필드명을 그대로 담으며 어댑터 밖으로
 * 나가지 않는다. 미정의 필드는 무시해 관대하게 역직렬화한다([TourApiResponse]와 같은 관례).
 *
 * **[TourApiResponse]를 재사용하지 않는 이유는 item의 필드가 다르기 때문이다.** 축제 응답에는 4개
 * 전용 필드가 있고, 반대로 `lclsSystm2`·`cpyrhtDivCd` 같은 것은 이 경로가 쓰지 않는다. 한 DTO에
 * 합치면 어느 오퍼레이션이 어떤 필드를 주는지가 흐려진다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class TourApiFestivalResponse(
    val response: Response = Response(),
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Response(
        val body: Body = Body(),
    )

    /**
     * `header`·`numOfRows`·`pageNo`는 선언하지 않는다. [TourApiResponse]에는 있으나 읽는 코드가 없고,
     * 페이지 진행 상태는 Tasklet이 소유한다. `@JsonIgnoreProperties(ignoreUnknown = true)`가 응답에
     * 실제로 오는 그 필드들을 무시하므로 역직렬화에는 영향이 없다.
     */
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
     * 축제 1건. 적재에 필요한 것만 선언한다 — 제목·주소·좌표는 `areaBasedSyncList2` 경로가 이미
     * `spot`에 적재하므로 여기서 읽지 않는다(AC-3).
     *
     * 날짜는 `YYYYMMDD` 문자열로 온다. `progresstype`/`festivaltype`은 공식 코드표가 없어 원문 그대로
     * 둔다(R-5). 두 필드의 설명이 서로 뒤바뀌어 있는 것으로 보이나([TBD-1]) 값을 해석하지 않으므로
     * 적재에는 영향이 없다.
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
