package org.around.ieuri.community.adapter.graphql.connection

import org.around.ieuri.community.application.post.provided.dto.PostView

data class PostEdge(
    val cursor: String,
    val node: PostView,
)
