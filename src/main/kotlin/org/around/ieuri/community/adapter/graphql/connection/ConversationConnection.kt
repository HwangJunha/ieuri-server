package org.around.ieuri.community.adapter.graphql.connection

import org.around.ieuri.common.graphql.generated.types.PageInfo

/**
 * DM 커넥션 응답 타입(SPEC-0017). [PostConnection]처럼 노드에 read DTO를 싣는다.
 * - 클래스 이름은 반드시 `Connection`으로 끝나야 한다. 아니면 런타임에 실패한다.
 */
data class ConversationConnection(
    val edges: List<ConversationEdge>,
    val pageInfo: PageInfo,
)
