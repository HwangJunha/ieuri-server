package org.around.ieuri.community.application.query

import java.time.Instant

// 장소 질문/답변 읽기(CQRS) read model(SPEC-0026). 한 행이 가진 것만 담고 나머지는 DataLoader가 읽는다.

data class SpotQuestionView(
    val id: Long,
    /** 이 질문이 달린 장소. 언어와 무관한 카탈로그 id다. */
    val catalogId: Long,
    val body: String,
    val authorId: Long,
    val resolved: Boolean,
    /** 숨겨진 답변을 제외한 수. 모든 호출자에게 같은 값이다. */
    val answerCount: Int,
    val ownedByViewer: Boolean,
    val hidden: Boolean,
    val createdAt: Instant,
)

data class SpotAnswerView(
    val id: Long,
    val questionId: Long,
    val body: String,
    val authorId: Long,
    val ownedByViewer: Boolean,
    /** 이 답변 자체가 숨겨졌는지. 질문이 숨겨져 함께 가려질 때는 바뀌지 않는다(R11). */
    val hidden: Boolean,
    val createdAt: Instant,
)

data class SpotAnswerReplyView(
    val id: Long,
    val answerId: Long,
    val body: String,
    val authorId: Long,
    val ownedByViewer: Boolean,
    val hidden: Boolean,
    val createdAt: Instant,
)

/**
 * 질문 목록 조회 입력. 정렬은 고정이다(POL-019).
 * - 세 필터는 독립적이며 함께 걸린다. 전부 비면 전체 질문이 대상이다.
 */
data class SpotQuestionsQuery(
    /** 이 장소의 질문만. null이면 장소로 좁히지 않는다. */
    val catalogId: Long?,
    /** true면 호출자가 쓴 질문만. 비로그인에게는 허용되지 않는다(리졸버가 막는다). */
    val mine: Boolean = false,
    /** 이 id들만. null이면 id로 좁히지 않는다. 빈 목록은 "결과 없음"이다(전체가 아니다). */
    val ids: List<Long>? = null,
    val first: Int,
    val after: String?,
)

data class SpotAnswersQuery(
    val questionId: Long,
    val first: Int,
    val after: String?,
)
