package org.around.ieuri.community.adapter.graphql

import org.around.ieuri.common.graphql.generated.types.PageInfo
import org.around.ieuri.community.application.question.provided.dto.SpotAnswerView
import org.around.ieuri.community.application.question.provided.dto.SpotQuestionView

/**
 * 장소 질문/답변 커넥션 응답 타입(SPEC-0026). [PostConnection]처럼 노드에 read DTO를 싣는다.
 * - 클래스 이름은 반드시 `Connection`으로 끝나야 한다. 아니면 런타임에 `No ConnectionAdapter for: ...`로 실패한다.
 */
data class SpotQuestionConnection(
    val edges: List<SpotQuestionEdge>,
    val pageInfo: PageInfo,
)

data class SpotQuestionEdge(
    val cursor: String,
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
