package org.around.ieuri.community.adapter.graphql.connection

import org.around.ieuri.common.graphql.generated.types.PageInfo

data class MessageConnection(
    val edges: List<MessageEdge>,
    val pageInfo: PageInfo,
)
