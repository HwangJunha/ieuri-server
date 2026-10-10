package org.around.ieuri.tourism.adapter.client.tourapi

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import tools.jackson.databind.annotation.JsonDeserialize
/**
 * TourAPI detailIntro2 응답 DTO. 카테고리(contentTypeId)마다 필드가 달라 변형을 모두 nullable로 담는다.
 * - 어댑터가 의미가 같은 것끼리 공통 도메인 필드로 정규화한다.
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
        val usetime: String? = null, // 12 관광지
        val usetimeculture: String? = null, // 14 문화시설
        val usetimefestival: String? = null, // 15 축제
        val usetimeleports: String? = null, // 28 레포츠
        val opentime: String? = null, // 38 쇼핑 (주의: opentimeshopping 아님)
        val opentimefood: String? = null, // 39 음식점
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
        val usefee: String? = null, // 14 문화시설
        val usefeeleports: String? = null, // 28 레포츠
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
