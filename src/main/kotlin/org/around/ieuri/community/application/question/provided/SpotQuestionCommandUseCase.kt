package org.around.ieuri.community.application.question.provided

import org.around.ieuri.community.application.question.provided.dto.CreateSpotAnswerCommand
import org.around.ieuri.community.application.question.provided.dto.CreateSpotAnswerReplyCommand
import org.around.ieuri.community.application.question.provided.dto.CreateSpotQuestionCommand
import org.around.ieuri.community.domain.shared.ContentLanguage
import java.time.Instant

/** 장소 질문 애그리게이트 쓰기(질문·답변·답글). 장소 반응 여부로 막지 않는다. */
interface SpotQuestionCommandUseCase {
    /** 생성된 질문 id. 장소는 [language]에서 해소되는 것만 쓸 수 있다(아니면 SPOT_NOT_FOUND_IN_LOCALE). */
    fun createQuestion(command: CreateSpotQuestionCommand, memberId: Long, language: ContentLanguage): Long

    /** 딸린 답변·답글(타인의 것 포함)도 함께 삭제된다. */
    fun deleteQuestion(questionId: Long, memberId: Long)

    /** 해결·미해결 양방향이며 양쪽 다 멱등이다. 답변은 영향받지 않는다. */
    fun resolveQuestion(questionId: Long, resolved: Boolean, memberId: Long)

    /** 질문의 답변 수를 함께 올린다. */
    fun createAnswer(command: CreateSpotAnswerCommand, memberId: Long): Long

    /** 딸린 답글도 함께 삭제된다. */
    fun deleteAnswer(answerId: Long, memberId: Long)

    /** parentAnswerId가 답변이 아니라 답글을 가리키면 REPLY_TO_REPLY_NOT_ALLOWED. 답글은 어떤 수에도 잡히지 않는다. */
    fun createReply(command: CreateSpotAnswerReplyCommand, memberId: Long): Long

    fun deleteReply(replyId: Long, memberId: Long)

    /** 탈퇴 정리. 탈퇴 커밋 뒤 리스너가 부르며 새 트랜잭션이다. 한 번의 탈퇴는 모든 애그리게이트에 같은 [deletedAt]을 남긴다. 바뀐 행 수(로그용). */
    fun eraseMemberContent(memberId: Long, deletedAt: Instant): Int

    /** 신고 누적 숨김. */
    fun hideReportedQuestion(questionId: Long)

    /** 신고 누적 숨김. 이번 호출로 숨겨졌을 때만 질문의 답변 수를 내린다. */
    fun hideReportedAnswer(answerId: Long)

    /** 신고 누적 숨김. 답글은 어떤 수에도 잡히지 않는다. */
    fun hideReportedReply(replyId: Long)
}
