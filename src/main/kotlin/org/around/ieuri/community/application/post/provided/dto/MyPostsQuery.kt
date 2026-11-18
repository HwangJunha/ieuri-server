package org.around.ieuri.community.application.post.provided.dto

/** 내가 쓴 글 조회 조건(SPEC-0011 R3). 위치·카테고리로 좁히지 않는다. */
data class MyPostsQuery(
    val first: Int,
    val after: String?,
)
