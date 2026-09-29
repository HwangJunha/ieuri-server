package org.around.ieuri.community.application.port.`in`.command

import org.around.ieuri.community.application.command.CreateSpotAnswerCommand
import org.around.ieuri.community.application.command.CreateSpotAnswerReplyCommand
import org.around.ieuri.community.application.command.CreateSpotQuestionCommand
import org.around.ieuri.community.application.command.DeleteSpotAnswerCommand
import org.around.ieuri.community.application.command.DeleteSpotAnswerReplyCommand
import org.around.ieuri.community.application.command.DeleteSpotQuestionCommand
import org.around.ieuri.community.application.command.ResolveSpotQuestionCommand
import org.around.ieuri.community.domain.vo.ContentLanguage

/** 장소 질문 쓰기 유스케이스(SPEC-0026). 반응 여부로 게이팅하지 않는다(R5). */
interface SpotQuestionCommandUseCase {
    /**
     * 생성된 질문 id. 장소는 [language] 기준으로 검사한다(SPEC-0029):
     * 카탈로그가 없으면 SPOT_NOT_FOUND, 이 언어에 원본이 없으면 SPOT_NOT_FOUND_IN_LOCALE.
     */
    fun createQuestion(
        command: CreateSpotQuestionCommand,
        memberId: Long,
        language: ContentLanguage,
    ): Long

    /** 딸린 답변·답글도 함께 삭제된다(AC-10.3). */
    fun deleteQuestion(command: DeleteSpotQuestionCommand, memberId: Long)

    /** 해결·미해결 양방향이며 양쪽 다 멱등이다(R8). */
    fun resolveQuestion(command: ResolveSpotQuestionCommand, memberId: Long)
}

/** 답변·답글 쓰기 유스케이스. 질문과 같이 반응으로 게이팅하지 않는다(R5). */
interface SpotAnswerCommandUseCase {
    fun createAnswer(command: CreateSpotAnswerCommand, memberId: Long): Long

    /** 딸린 답글도 함께 삭제된다(AC-10.4). */
    fun deleteAnswer(command: DeleteSpotAnswerCommand, memberId: Long)

    /** parentAnswerId가 답글을 가리키면 REPLY_TO_REPLY_NOT_ALLOWED(R7). */
    fun createReply(command: CreateSpotAnswerReplyCommand, memberId: Long): Long

    fun deleteReply(command: DeleteSpotAnswerReplyCommand, memberId: Long)
}
