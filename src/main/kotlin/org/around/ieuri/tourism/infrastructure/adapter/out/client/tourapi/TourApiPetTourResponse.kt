package org.around.ieuri.tourism.infrastructure.adapter.out.client.tourapi

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * TourAPI detailPetTour2 응답 DTO(SPEC-0015 · V48). **어댑터 내부 전용** — TourAPI 필드명을
 * 그대로 담으며 어댑터 밖으로 나가지 않는다.
 *
 * 봉투 구조는 [TourApiResponse]와 같지만 item 필드가 전혀 겹치지 않아 재사용하지 않는다 —
 * 한 DTO에 두 오퍼레이션의 필드를 합치면 어느 쪽이 채워지는지 타입으로 알 수 없게 된다.
 *
 * `rela*` 계열(relaAcdntRiskMtr 등)은 실측상 대부분 비어 있어 매핑하지 않는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class TourApiPetTourResponse(
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
        val acmpyTypeCd: String? = null,
        val acmpyPsblCpam: String? = null,
        val acmpyNeedMtr: String? = null,
        val etcAcmpyInfo: String? = null,
    )
}
