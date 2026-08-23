package org.around.ieuri.tourism.application.policy

import java.time.Duration

/**
 * spots 조회 정책값(설정에서 주입). 반경 UX/성능 상한과 페이지 크기 캡을 코드 수정 없이 조정한다.
 *
 * @param maxRadiusMeters 서버 UX/성능 반경 상한(실측 확정 100,000m). 초과 시 RADIUS_TOO_LARGE.
 * @param maxPageSize     `spots` 오프셋 페이지의 size 상한(POL-018 잠정 100). 초과 요청은 클램프.
 * @param maxLikedPageSize `likedSpots` 커서 페이지의 first 상한(SPEC-0008 [TBD-2], `members` 선례로 100).
 *   [maxPageSize]와 우연히 값이 같지만 **다른 계약이다** — 하나는 위치 기반 오프셋 목록, 다른 하나는
 *   회원의 좋아요 커서 목록이다. 한 노브로 묶으면 `spots` 상한을 조정할 때 좋아요 목록이 조용히
 *   함께 바뀐다.
 * @param detailAbsenceTtl 상세 본문 **부재** 행의 신선도 기준(SPEC-0008 [TBD-1] = 7일).
 *   이 기간이 지나면 부재 spot을 upstream에 다시 물어본다(AC-13) — 나중에 데이터가 생길 수 있기
 *   때문이다. 정상 행에는 만료가 없다(1차 신선도는 Valkey TTL 24h, 배치가 목록을 매일 최신화).
 *   부재를 정상보다 짧게 잡는 것이 이 값의 요지이며, 실측 후 조정 대상이라 설정값으로 둔다.
 * @param maxSearchPageSize `searchSpots`·`ongoingFestivals`·`popularSpots` 커서 페이지의 first 상한
 *   (SPEC-0013, POL-018 = 50). 초과 요청은 거절이 아니라 클램프다.
 * @param maxSuggestionCount `spotSearchSuggestions`의 limit 상한(= 10). 기본값과 상한이 같다 —
 *   자동완성은 지연에 민감하고 화면이 그 이상을 보여주지 않는다.
 * @param maxSearchQueryLength 검색어 길이 상한(= 100자). 초과 시 QUERY_TOO_LONG(AC-8.3).
 *   사용자 입력이 검색 엔진에 전달되는 최초 경로라 상한을 명시한다.
 * @param maxSearchPage `searchSpots`의 페이지 상한(= 500). 초과 시 PAGE_TOO_DEEP.
 *   OFFSET 비용이 깊이에 비례해 커져 두는 제약이며, 검색 엔진들이 공통으로 갖는다
 *   (Elasticsearch의 기본 결과 창이 10,000건). 크기 20 기준 10,000건에 해당한다.
 * @param minPopularLikeCount `popularSpots` 진입 하한. 표본이 적으면 순위가 사실상 잡음이라
 *   집계 후 이 수 미만을 버린다(AC-18.2). 현재 실데이터로는 결과가 비는 것이 정상이다(AC-18.4).
 *   설정값이며 코드에 값을 적지 않는다(POL-044).
 */
data class SpotQueryPolicy(
    val maxRadiusMeters: Int,
    val maxPageSize: Int,
    val maxLikedPageSize: Int,
    val detailAbsenceTtl: Duration,
    val maxSearchPageSize: Int,
    val maxSuggestionCount: Int,
    val maxSearchQueryLength: Int,
    val maxSearchPage: Int,
    val minPopularLikeCount: Int,
)
