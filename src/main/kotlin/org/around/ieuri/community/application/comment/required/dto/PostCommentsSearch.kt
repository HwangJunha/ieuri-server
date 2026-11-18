package org.around.ieuri.community.application.comment.required.dto

import org.around.ieuri.community.application.shared.required.dto.ViewerContext

data class PostCommentsSearch(
    val postId: Long,
    val first: Int,
    val after: String?,
    val viewer: ViewerContext,
)
