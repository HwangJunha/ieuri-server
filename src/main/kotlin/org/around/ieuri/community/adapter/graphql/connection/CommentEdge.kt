package org.around.ieuri.community.adapter.graphql.connection

import org.around.ieuri.community.application.comment.provided.dto.CommentView

data class CommentEdge(
    val cursor: String,
    val node: CommentView,
)
