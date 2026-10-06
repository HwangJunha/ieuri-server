package org.around.ieuri.tourism.application.shared

import java.time.Duration

/**
 * spots 조회 정책값(설정에서 주입).
 *
 * @param maxRadiusMeters 반경 상한(m). 초과 시 RADIUS_TOO_LARGE.
 * @param maxPageSize `spots` 오프셋 페이지의 size 상한. 초과 요청은 클램프.
 * @param maxLikedPageSize `likedSpots` 커서 페이지의 first 상한. [maxPageSize]와 별개 계약이다.
 * @param maxSearchPageSize `searchSpots`·`ongoingFestivals`·`popularSpots`의 first 상한. 초과 요청은 클램프.
 * @param maxSuggestionCount `spotSearchSuggestions`의 limit 상한.
 * @param maxSearchQueryLength 검색어 길이 상한. 초과 시 QUERY_TOO_LONG(AC-8.3).
 * @param maxSearchPage `searchSpots`의 페이지 상한. 초과 시 PAGE_TOO_DEEP.
 * @param minPopularLikeCount `popularSpots` 진입 하한. 집계 후 이 수 미만을 버린다(AC-18.2).
 */
data class SpotQueryPolicy(
    val maxRadiusMeters: Int,
    val maxPageSize: Int,
    val maxLikedPageSize: Int,
    val maxSearchPageSize: Int,
    val maxSuggestionCount: Int,
    val maxSearchQueryLength: Int,
    val maxSearchPage: Int,
    val minPopularLikeCount: Int,
)
