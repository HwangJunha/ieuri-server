package org.around.ieuri.tourism.adapter.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * TourAPI 접근 설정. serviceKey는 4개 언어 공용이며 환경변수로만 주입한다(응답·로그·GraphQL 노출 금지).
 *
 * @param baseUrls 언어(KO/EN/JA/ZH)→base-url.
 * @param excludeLclssystm2 비관광 제외 lclsSystm2 코드 목록(다국어 공통 적용).
 * @param arrange 정렬/필터 코드. Q=이미지 보유 항목만 + 수정일순.
 * @param showflag 표출 여부 필터. 1=표출 중인 항목만. 응답에서도 다시 확인한다.
 * @param maxRadiusMeters 반경 상한. 초과 시 RADIUS_TOO_LARGE.
 * @param maxPageSize `spots` size 상한. 초과 요청은 클램프.
 * @param maxLikedPageSize `likedSpots` first 상한.
 * @param maxSearchQueryLength 검색어 길이 상한(자). 초과 시 QUERY_TOO_LONG.
 * @param maxSearchPage 검색 페이지 상한. 초과 시 PAGE_TOO_DEEP.
 */
@ConfigurationProperties(prefix = "ieuri.tourapi")
data class TourApiProperties(
    val serviceKey: String = "",
    val baseUrls: Map<String, String> = emptyMap(),
    val excludeLclssystm2: List<String> = emptyList(),
    val arrange: String = "Q",
    val showflag: String = "1",
    val mobileOs: String = "ETC",
    val mobileApp: String = "ieuri",
    val maxRadiusMeters: Int = 100_000,
    val maxPageSize: Int = 100,
    val maxLikedPageSize: Int = 100,
    val maxSearchPageSize: Int = 50,
    val maxSuggestionCount: Int = 10,
    val maxSearchQueryLength: Int = 100,
    val maxSearchPage: Int = 500,
    val minPopularLikeCount: Int = 5,
    val connectTimeoutMillis: Long = 15_000,
    val readTimeoutMillis: Long = 30_000,
) {
    companion object {
        const val CODESET_KO = "KO"
        const val CODESET_FOREIGN = "FOREIGN"
    }
}
