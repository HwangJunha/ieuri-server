package org.around.ieuri.community.application.event

/**
 * 글 상세가 조회되었다. 조회수 증가는 이 이벤트를 받아 **비동기로** 처리한다 —
 * 조회 응답이 UPDATE를 기다릴 이유가 없다.
 *
 * 피드 목록에서는 발행하지 않는다. 카드가 스쳐 지나가는 것은 조회가 아니며,
 * 그것까지 세면 스크롤 한 번에 수십이 올라 숫자가 의미를 잃는다.
 */
data class PostViewed(val postId: Long)
