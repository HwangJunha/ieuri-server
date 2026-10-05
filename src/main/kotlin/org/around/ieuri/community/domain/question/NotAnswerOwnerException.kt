package org.around.ieuri.community.domain.question

import org.around.ieuri.community.domain.shared.CommunityUserException

class NotAnswerOwnerException(message: String = "본인의 답변만 삭제할 수 있습니다.") : CommunityUserException("NOT_ANSWER_OWNER", message)
