package org.around.ieuri.community.domain.question

import org.around.ieuri.community.domain.shared.CommunityUserException

class NotQuestionOwnerException(message: String = "본인의 질문만 처리할 수 있습니다.") : CommunityUserException("NOT_QUESTION_OWNER", message)
