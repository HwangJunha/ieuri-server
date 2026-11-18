package org.around.ieuri.community.adapter.graphql.connection

import org.around.ieuri.common.graphql.generated.types.PageInfo

data class CommentConnection(
    val edges: List<CommentEdge>,
    val pageInfo: PageInfo,
)
