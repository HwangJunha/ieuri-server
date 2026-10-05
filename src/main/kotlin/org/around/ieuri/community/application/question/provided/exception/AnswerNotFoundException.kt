package org.around.ieuri.community.application.question.provided.exception

import org.around.ieuri.community.domain.shared.CommunityUserException

class AnswerNotFoundException(message: String = "해당 답변을 찾을 수 없습니다.") : CommunityUserException("ANSWER_NOT_FOUND", message)
