package org.around.ieuri.community.application.port.out.persistence

import org.around.ieuri.community.domain.aggregate.SpotAnswer
import org.around.ieuri.community.domain.aggregate.SpotAnswerReply
import org.around.ieuri.community.domain.aggregate.SpotQuestion

/** 질문 애그리게이트 적재·저장(SPEC-0026). 답변 수 증감은 [SpotQuestionCounterPort]를 쓴다. */
interface SpotQuestionRepositoryPort {
    /** 삭제되지 않은 질문만. 없으면 null. */
    fun findById(questionId: Long): SpotQuestion?

    fun save(question: SpotQuestion): SpotQuestion

    /** 질문과 그 답변·답글을 물리 삭제한다(FK CASCADE). 사진 행도 함께 지운다. */
    fun deleteWithDescendants(questionId: Long)
}

/**
 * 질문의 답변 수를 애그리게이트를 거치지 않고 원자적으로 증감한다.
 * - 삭제된 질문은 갱신하지 않으며, 대상이 없어도 오류가 아니다(0건 갱신).
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

/** 질문·답변·답글의 존재 확인과 신고 누적 자동 숨김. 애그리게이트를 복원하지 않는다. */
interface SpotQaModerationPort {
    /** 삭제되지 않은 질문이 있는지. 신고 대상 확인이 쓴다. */
    fun questionExists(questionId: Long): Boolean

    fun answerExists(answerId: Long): Boolean

    fun replyExists(replyId: Long): Boolean

    /** 이미 숨겨져 있으면 아무것도 하지 않고 false — 호출자가 중복 처리를 건너뛴다. */
    fun hideQuestion(questionId: Long): Boolean

    /** 답변을 숨기고 그 질문 id를 돌려준다(answerCount 감소용). 이미 숨겨져 있었으면 null. */
    fun hideAnswer(answerId: Long): Long?

    fun hideReply(replyId: Long): Boolean
}
