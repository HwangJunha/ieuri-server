package org.around.ieuri.community.application.qa.provided.exception

import org.around.ieuri.community.domain.shared.CommunityUserException

class QuestionNotFoundException(message: String = "해당 질문을 찾을 수 없습니다.") : CommunityUserException("QUESTION_NOT_FOUND", message)
