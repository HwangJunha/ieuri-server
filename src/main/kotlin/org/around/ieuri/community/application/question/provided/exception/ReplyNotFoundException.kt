package org.around.ieuri.community.application.question.provided.exception

import org.around.ieuri.community.domain.shared.CommunityUserException

class ReplyNotFoundException(message: String = "해당 답글을 찾을 수 없습니다.") : CommunityUserException("REPLY_NOT_FOUND", message)
