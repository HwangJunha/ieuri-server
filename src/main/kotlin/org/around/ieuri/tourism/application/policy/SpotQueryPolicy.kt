package org.around.ieuri.tourism.application.policy

/**
 * spots 조회 정책값(설정에서 주입). 반경 UX/성능 상한과 페이지 크기 캡을 코드 수정 없이 조정한다.
 *
 * @param maxRadiusMeters 서버 UX/성능 반경 상한(실측 확정 100,000m). 초과 시 RADIUS_TOO_LARGE.
 * @param maxPageSize     first 상한(POL-018 잠정 100). 초과 요청은 클램프.
 * @param upstreamPageSize TourAPI numOfRows(실측 100). upstream 한 페이지당 건수.
 * @param maxUpstreamPagesPerCursor 커서 한 번 채우기 위해 병합할 upstream 페이지 상한(무한 루프·과호출 방지).
 */
data class SpotQueryPolicy(
    val maxRadiusMeters: Int,
    val maxPageSize: Int,
    val upstreamPageSize: Int,
    val maxUpstreamPagesPerCursor: Int,
)
