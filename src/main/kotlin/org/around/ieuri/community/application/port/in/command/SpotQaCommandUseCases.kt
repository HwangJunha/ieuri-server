package org.around.ieuri.community.application.port.`in`.command

import org.around.ieuri.community.application.command.CreateSpotAnswerCommand
import org.around.ieuri.community.application.command.CreateSpotAnswerReplyCommand
import org.around.ieuri.community.application.command.CreateSpotQuestionCommand
import org.around.ieuri.community.application.command.DeleteSpotAnswerCommand
import org.around.ieuri.community.application.command.DeleteSpotAnswerReplyCommand
import org.around.ieuri.community.application.command.DeleteSpotQuestionCommand
import org.around.ieuri.community.application.command.ResolveSpotQuestionCommand

/**
 * 장소 질문 쓰기 유스케이스(SPEC-0026).
 *
 * 작성 자격(그 장소를 좋아요했는지)은 **이 계층이 판정한다** — 다른 애그리게이트의 상태라
 * 질문 도메인이 알면 경계가 무너지기 때문이다. 판정 시점은 화면 진입이 아니라 이 커맨드를
 * 처리하는 순간이다(R5).
 */
interface SpotQuestionCommandUseCase {
    /** 생성된 질문 id. 자격이 없으면 NOT_ELIGIBLE_TO_ASK. */
    fun createQuestion(command: CreateSpotQuestionCommand, memberId: Long): Long

    /** 딸린 답변·답글도 함께 삭제된다(AC-10.3). */
    fun deleteQuestion(command: DeleteSpotQuestionCommand, memberId: Long)

    /** 이미 해결됨이어도 성공이다 — 멱등하며 되돌리는 경로는 없다(R8). */
    fun resolveQuestion(command: ResolveSpotQuestionCommand, memberId: Long)
}

/**
 * 답변·답글 쓰기 유스케이스. 답글도 답변과 **같은 자격**을 요구한다(AC-7.3) — 답글로
 * 우회 참여가 되면 게이팅이 무의미해진다.
 */
interface SpotAnswerCommandUseCase {
    fun createAnswer(command: CreateSpotAnswerCommand, memberId: Long): Long

    /** 딸린 답글도 함께 삭제된다(AC-10.4). */
    fun deleteAnswer(command: DeleteSpotAnswerCommand, memberId: Long)

    /** parentAnswerId가 답글을 가리키면 REPLY_TO_REPLY_NOT_ALLOWED(R7). */
    fun createReply(command: CreateSpotAnswerReplyCommand, memberId: Long): Long

    fun deleteReply(command: DeleteSpotAnswerReplyCommand, memberId: Long)
}
