package org.around.ieuri.community.application.qa.provided

import org.around.ieuri.community.application.qa.provided.dto.CreateSpotAnswerCommand
import org.around.ieuri.community.domain.shared.CommunityUserException

/** 답변 쓰기. 질문의 답변 수를 함께 바꾼다. */
interface SpotAnswerCommandUseCase {
    fun createAnswer(command: CreateSpotAnswerCommand, memberId: Long): Long

    /** 딸린 답글도 함께 삭제된다. */
    fun deleteAnswer(answerId: Long, memberId: Long)
}

class AnswerNotFoundException(message: String = "해당 답변을 찾을 수 없습니다.") : CommunityUserException("ANSWER_NOT_FOUND", message)
