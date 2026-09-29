package org.around.ieuri.tourism.application.policy

/**
 * 전국 지도 조회 정책값(설정에서 주입, POL-044).
 * - 칸 상세 spot·관계 목록의 first 상한 초과는 클램프다(POL-018).
 * - [shareUrlBase] 뒤에 토큰을 붙여 공유 링크를 만든다. 메신저가 `ieuri://`를 링크로 인식하지 않으므로 https여야 한다.
 */
data class NationwideMapPolicy(
    val maxCellSpotPageSize: Int,
    val shareUrlBase: String,
    val maxRelationPageSize: Int,
)
