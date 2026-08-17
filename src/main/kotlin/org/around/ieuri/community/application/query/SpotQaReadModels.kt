package org.around.ieuri.community.application.query

import java.time.Instant

/**
 * 장소 질문/답변 읽기(CQRS) 전용 read model(SPEC-0026).
 *
 * [PostView]와 같은 규약이다 — **한 행이 가진 것만 담는다.** 사진·작성자·장소·답글은 각자
 * 다른 테이블이라 여기 없고, 클라이언트가 그 필드를 고른 경우에만 DataLoader가 한 번에 읽는다.
 */

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
    /**
     * 이 답변 **자체가** 숨겨졌는지. 질문이 숨겨져 함께 가려지는 경우 이 값은 바뀌지 않는다 —
     * 질문이 복구되면 답변도 함께 돌아와야 하기 때문이다(R11).
     */
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

/** 질문 목록 조회 입력. 정렬 인자가 없다 — 정렬은 계약이다(POL-019, R2). */
data class SpotQuestionsQuery(
    val catalogId: Long,
    val first: Int,
    val after: String?,
)

data class SpotAnswersQuery(
    val questionId: Long,
    val first: Int,
    val after: String?,
)
