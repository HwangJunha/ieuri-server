package org.around.ieuri.tourism.application.policy

/**
 * spots 조회 정책값(설정에서 주입). 반경 UX/성능 상한과 페이지 크기 캡을 코드 수정 없이 조정한다.
 *
 * @param maxRadiusMeters 서버 UX/성능 반경 상한(실측 확정 100,000m). 초과 시 RADIUS_TOO_LARGE.
 * @param maxPageSize     size 상한(POL-018 잠정 100). 초과 요청은 클램프.
 */
data class SpotQueryPolicy(
    val maxRadiusMeters: Int,
    val maxPageSize: Int,
)
