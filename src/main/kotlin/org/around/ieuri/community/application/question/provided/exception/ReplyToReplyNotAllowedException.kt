package org.around.ieuri.community.application.question.provided.exception

import org.around.ieuri.community.domain.shared.CommunityUserException

class ReplyToReplyNotAllowedException(message: String = "답글에는 답글을 달 수 없습니다.") : CommunityUserException("REPLY_TO_REPLY_NOT_ALLOWED", message)
