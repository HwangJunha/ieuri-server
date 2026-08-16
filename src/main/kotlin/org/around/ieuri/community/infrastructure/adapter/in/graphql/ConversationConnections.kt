package org.around.ieuri.community.infrastructure.adapter.`in`.graphql

import org.around.ieuri.common.graphql.generated.types.PageInfo
import org.around.ieuri.community.application.query.ConversationView
import org.around.ieuri.community.application.query.MessageView

/**
 * DM 커넥션 응답 타입(SPEC-0017). **생성 타입을 쓰지 않고 직접 둔다** — 이유는
 * [PostConnection]과 같다(생성된 노드가 non-null 필드를 생성자에서 요구해 지연 필드를
 * 비워 둘 수 없다).
 *
 * **클래스 이름이 반드시 `Connection`으로 끝나야 한다** — Spring for GraphQL이 그 이름으로
 * 커넥션 래핑 통과 여부를 판정하며, 어긋나면 컴파일은 되고 조회가 런타임에 터진다.
 */
data class ConversationConnection(
    val edges: List<ConversationEdge>,
    val pageInfo: PageInfo,
)

data class ConversationEdge(
    val cursor: String,
    val node: ConversationView,
)

data class MessageConnection(
    val edges: List<MessageEdge>,
    val pageInfo: PageInfo,
)

data class MessageEdge(
    val cursor: String,
    val node: MessageView,
)
