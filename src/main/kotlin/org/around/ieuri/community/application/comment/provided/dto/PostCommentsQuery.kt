package org.around.ieuri.community.application.comment.provided.dto

data class PostCommentsQuery(
    val postId: Long,
    val first: Int,
    val after: String?,
)
