package org.around.ieuri.community.application.event

/** 글 상세가 조회되었다. 조회수는 이 이벤트로 비동기 증가하며, 피드 목록에서는 발행하지 않는다. */
data class PostViewed(val postId: Long)
