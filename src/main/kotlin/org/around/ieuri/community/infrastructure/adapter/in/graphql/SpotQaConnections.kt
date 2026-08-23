package org.around.ieuri.community.infrastructure.adapter.`in`.graphql

import org.around.ieuri.common.graphql.generated.types.PageInfo
import org.around.ieuri.community.application.query.SpotAnswerView
import org.around.ieuri.community.application.query.SpotQuestionView

/**
 * 장소 질문/답변의 커넥션 응답 타입(SPEC-0026). **생성 타입을 쓰지 않고 직접 둔다** —
 * [PostConnection]과 같은 근거다.
 *
 * 생성된 `SpotQuestionEdge.node`는 `SpotQuestion` 타입인데, 그 타입은 spot·author·photoKeys가
 * non-null이라 생성자에서 값을 요구한다 — 지연 필드를 비워 둘 수 없다는 뜻이다. 노드로
 * read DTO를 실어야 @DgsData 필드 리졸버가 요청된 경우에만 조회한다.
 *
 * **클래스 이름이 반드시 `Connection`으로 끝나야 한다** — Spring for GraphQL의
 * `ConnectionFieldTypeVisitor`가 이름으로 통과를 판정하고, 실패하면 런타임에
 * `No ConnectionAdapter for: ...`로 터진다. 컴파일은 통과하므로 이름을 바꿀 때 주의할 것.
 */
data class SpotQuestionConnection(
    val edges: List<SpotQuestionEdge>,
    val pageInfo: PageInfo,
)

data class SpotQuestionEdge(
    val cursor: String,
    /** read DTO. GraphQL `SpotQuestion`의 필드는 이 값에서 리졸브된다. */
    val node: SpotQuestionView,
)

data class SpotAnswerConnection(
    val edges: List<SpotAnswerEdge>,
    val pageInfo: PageInfo,
)

data class SpotAnswerEdge(
    val cursor: String,
    val node: SpotAnswerView,
)
