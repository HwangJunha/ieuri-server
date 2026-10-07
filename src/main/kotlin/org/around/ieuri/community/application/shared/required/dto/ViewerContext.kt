package org.around.ieuri.community.application.shared.required.dto

/**
 * 조회 호출자와 그 차단 관계. 피드·단건·댓글 조회가 모두 이 값을 쿼리 조건으로 건다.
 * - [blockedMemberIds]는 "내가 차단한"과 "나를 차단한"의 합집합이다.
 */
data class ViewerContext(
    val memberId: Long,
    val blockedMemberIds: Set<Long>,
    /** 호출자가 개별로 숨긴 글의 id. */
    val hiddenPostIds: Set<Long> = emptySet(),
)
