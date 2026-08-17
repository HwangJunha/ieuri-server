package org.around.ieuri.community.application.port.out.persistence

import org.around.ieuri.community.domain.aggregate.SpotAnswer
import org.around.ieuri.community.domain.aggregate.SpotAnswerReply
import org.around.ieuri.community.domain.aggregate.SpotQuestion

/**
 * 질문 애그리게이트 적재·저장(SPEC-0026).
 *
 * [findById]는 사진까지 갖춰 복원하므로 조회가 두 번이다. 답변 수 증감처럼 일부만 필요한
 * 연산은 [SpotQuestionCounterPort]를 쓴다 — 그쪽이 싸고 동시성도 정확하다.
 */
interface SpotQuestionRepositoryPort {
    /** 삭제되지 않은 질문만. 없으면 null. */
    fun findById(questionId: Long): SpotQuestion?

    fun save(question: SpotQuestion): SpotQuestion

    /**
     * 질문과 그 답변·답글을 **물리 삭제한다**(AC-10.3). 사진 행도 함께 지운다.
     *
     * 소프트 삭제가 아닌 이유는 종속 삭제 때문이다 — 답변·답글까지 deleted_at을 칠하려면
     * 자식을 전부 훑어야 하는데, 그 수에 상한이 없다. FK CASCADE에 맡기면 한 문장이다.
     */
    fun deleteWithDescendants(questionId: Long)
}

/**
 * 질문의 답변 수 증감. **애그리게이트를 거치지 않는다** — [PostCounterPort]와 같은 근거로,
 * 읽고 고쳐 쓰면 동시 답변이 서로의 증가를 덮어쓴다.
 *
 * 삭제된 질문은 갱신하지 않으며, 대상이 없어도 오류가 아니다(0건 갱신).
 */
interface SpotQuestionCounterPort {
    fun increaseAnswerCount(questionId: Long)

    /** 0 아래로 내려가지 않는다 — DB의 CHECK 제약과 조건절이 함께 막는다. */
    fun decreaseAnswerCount(questionId: Long)
}

interface SpotAnswerRepositoryPort {
    /** 삭제되지 않은 답변만. 없으면 null. */
    fun findById(answerId: Long): SpotAnswer?

    fun save(answer: SpotAnswer): SpotAnswer

    /** 답변과 그 답글을 물리 삭제한다(AC-10.4). 사진 행도 함께 지운다. */
    fun deleteWithDescendants(answerId: Long)
}

interface SpotAnswerReplyRepositoryPort {
    fun findById(replyId: Long): SpotAnswerReply?

    fun save(reply: SpotAnswerReply): SpotAnswerReply

    fun delete(replyId: Long)
}

/**
 * 신고 누적에 따른 자동 숨김. 애그리게이트 전체를 복원할 이유가 없는 연산이다.
 *
 * 셋을 한 포트에 모은 이유는 호출자가 하나이기 때문이다 — ReportCommandService가 대상
 * 종류로 분기해 부른다.
 */
interface SpotQaModerationPort {
    /** 삭제되지 않은 질문이 있는지. 신고 대상 확인이 쓴다. */
    fun questionExists(questionId: Long): Boolean

    fun answerExists(answerId: Long): Boolean

    fun replyExists(replyId: Long): Boolean

    /** 이미 숨겨져 있으면 아무것도 하지 않고 false — 호출자가 중복 처리를 건너뛴다. */
    fun hideQuestion(questionId: Long): Boolean

    /**
     * 답변을 숨긴다. 실제로 숨겼으면 그 답변이 달린 질문 id를 돌려준다 — 호출자가 그
     * 질문의 answerCount를 줄이기 위해 필요하다. 이미 숨겨져 있었으면 null.
     */
    fun hideAnswer(answerId: Long): Long?

    fun hideReply(replyId: Long): Boolean
}
