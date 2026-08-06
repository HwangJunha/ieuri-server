package org.around.ieuri.community.infrastructure.adapter.`in`.graphql

import org.around.ieuri.common.graphql.generated.types.PageInfo
import org.around.ieuri.community.application.query.CommentView
import org.around.ieuri.community.application.query.PostView

/**
 * 커넥션 응답 타입. **생성 타입을 쓰지 않고 직접 둔다.**
 *
 * 생성된 `PostEdge.node`는 `Post` 타입인데, `Post`는 spot·author가 non-null이라 생성자에서
 * 값을 요구한다 — 지연 필드를 비워 둘 수 없다는 뜻이다. 노드로 read DTO를 실어야 @DgsData
 * 필드 리졸버가 요청된 경우에만 target·author를 조회할 수 있다.
 *
 * **클래스 이름이 반드시 `Connection`으로 끝나야 한다.** Spring for GraphQL의
 * `ConnectionFieldTypeVisitor`는 스키마가 Relay 커넥션으로 선언한 필드의 반환값을 감싸는데,
 * 등록된 `ConnectionAdapter`(Spring Data의 Window/Slice 전용)가 지원하지 않는 타입이면
 * `getClass().getName().endsWith("Connection")`으로 통과 여부를 판정하고, 실패하면
 * `No ConnectionAdapter for: ...`로 런타임에 터진다.
 *
 * 이름이 계약의 일부라는 뜻이다 — `...ConnectionResponse`처럼 뒤에 무엇을 붙이면 조회가
 * 통째로 실패한다. 컴파일은 통과하므로 이름을 바꿀 때 주의할 것.
 *
 * 생성 타입과 이름이 같지만 패키지가 달라 충돌하지 않는다(이 파일들은 생성 타입을 import하지 않는다).
 */
data class PostConnection(
    val edges: List<PostEdge>,
    val pageInfo: PageInfo,
)

data class PostEdge(
    val cursor: String,
    /** read DTO. GraphQL `Post`의 필드는 이 값에서 리졸브된다. */
    val node: PostView,
)

data class CommentConnection(
    val edges: List<CommentEdge>,
    val pageInfo: PageInfo,
)

data class CommentEdge(
    val cursor: String,
    val node: CommentView,
)
