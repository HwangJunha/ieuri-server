package org.around.ieuri.community.application.port.`in`.command

import org.around.ieuri.community.application.command.CreateSpotAnswerCommand
import org.around.ieuri.community.application.command.CreateSpotAnswerReplyCommand
import org.around.ieuri.community.application.command.CreateSpotQuestionCommand
import org.around.ieuri.community.application.command.DeleteSpotAnswerCommand
import org.around.ieuri.community.application.command.DeleteSpotAnswerReplyCommand
import org.around.ieuri.community.application.command.DeleteSpotQuestionCommand
import org.around.ieuri.community.application.command.ResolveSpotQuestionCommand
import org.around.ieuri.community.domain.vo.ContentLanguage

/**
 * 장소 질문 쓰기 유스케이스(SPEC-0026).
 *
 * **반응으로 게이팅하지 않는다**(R5) — 로그인한 회원이면 좋아요·싫어요·무반응과 무관하게
 * 쓸 수 있다. 남는 대상 검증은 장소가 실재하는가뿐이다.
 */
interface SpotQuestionCommandUseCase {
    /**
     * 생성된 질문 id.
     *
     * [language]는 요청의 활성 언어이며 **장소 존재 검사의 기준이다**(SPEC-0029 R-16) —
     * 카탈로그가 아예 없으면 SPOT_NOT_FOUND, 있으나 이 언어에 원본이 없으면
     * SPOT_NOT_FOUND_IN_LOCALE이다.
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
