package org.around.ieuri.community.application.question.required

import org.around.ieuri.community.domain.question.SpotAnswer
import org.around.ieuri.community.domain.question.SpotAnswerReply
import org.around.ieuri.community.domain.question.SpotQuestion
import org.around.ieuri.community.domain.shared.AttachedPhotos
import java.time.Instant

/**
 * 질문 애그리게이트 저장소. 루트와 자식은 각자 필요한 한 행만 읽는다.
 * - 해결 표시·숨김은 각자 한 열만, 답변 수는 원자적 증감만 쓴다. 읽기는 모두 삭제되지 않은 행만 본다.
 */
interface SpotQuestionRepository {
    fun findById(questionId: Long): SpotQuestion?

    /** 살아 있는 답변 [answerId]가 달린 질문. */
    fun findByAnswerId(answerId: Long): SpotQuestion?

    fun findAnswer(answerId: Long): SpotAnswer?

    fun findReply(replyId: Long): SpotAnswerReply?

    fun exists(questionId: Long): Boolean

    fun answerExists(answerId: Long): Boolean

    fun replyExists(replyId: Long): Boolean

    fun insert(question: SpotQuestion, photos: AttachedPhotos): Long

    fun insertAnswer(answer: SpotAnswer, photos: AttachedPhotos): Long

    fun insertReply(reply: SpotAnswerReply, photos: AttachedPhotos): Long

    /** 해결 표시만 쓴다. 그 사이 삭제됐으면 false. */
    fun changeResolved(questionId: Long, resolved: Boolean): Boolean

    /** 질문과 그 답변·답글, 그 모두의 사진을 물리 삭제한다. */
    fun deleteWithDescendants(questionId: Long)

    /** 답변과 그 답글·사진을 물리 삭제한다. 이번 호출로 지웠고 숨겨져 있지 않았으면 true — 그때만 답변 수에 잡혀 있었다. */
    fun deleteAnswerWasCounted(answerId: Long): Boolean

    /** 답글과 그 사진을 물리 삭제한다. */
    fun deleteReply(replyId: Long)

    // 신고 누적 숨김: 보이던 것이 이번 호출로 숨겨졌을 때만 결과가 있다.
    fun hideIfVisible(questionId: Long): Boolean

    /** 숨겨졌으면 그 질문 id. */
    fun hideAnswerIfVisible(answerId: Long): Long?

    fun hideReplyIfVisible(replyId: Long): Boolean

    // 답변 수: 삭제된 질문은 바꾸지 않고, 0 아래로 내려가지 않는다.
    fun increaseAnswerCount(questionId: Long)

    fun decreaseAnswerCount(questionId: Long)

    // 탈퇴 정리: 삭제 표시만 하고 남의 자식은 칠하지 않는다 — 부모가 지워진 자식은 읽기가 거른다.
    fun markDeletedByAuthor(authorId: Long, deletedAt: Instant): Int

    /** 이번에 지운 답변 중 수에 잡혀 있던(숨겨지지 않은) 것의 질문 id를 건마다 돌려준다. */
    fun markAnswersDeletedByAuthorCounted(authorId: Long, deletedAt: Instant): List<Long>

    fun markRepliesDeletedByAuthor(authorId: Long, deletedAt: Instant): Int
}
