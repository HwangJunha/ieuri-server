package org.around.ieuri.community.adapter.graphql.connection

import org.around.ieuri.common.graphql.generated.types.PageInfo

/**
 * 글·댓글 커넥션 응답 타입. 노드에 read DTO를 실으려고 생성 타입 대신 직접 둔다.
 * - 클래스 이름은 반드시 `Connection`으로 끝나야 한다. 아니면 런타임에 `No ConnectionAdapter for: ...`로 실패한다.
 */
data class PostConnection(
    val edges: List<PostEdge>,
    val pageInfo: PageInfo,
)
