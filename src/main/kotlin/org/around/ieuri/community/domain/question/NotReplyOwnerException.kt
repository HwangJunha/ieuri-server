package org.around.ieuri.community.domain.question

import org.around.ieuri.community.domain.shared.CommunityUserException

class NotReplyOwnerException(message: String = "본인의 답글만 삭제할 수 있습니다.") : CommunityUserException("NOT_REPLY_OWNER", message)
