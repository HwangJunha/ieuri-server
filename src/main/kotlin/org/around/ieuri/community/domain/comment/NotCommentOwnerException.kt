package org.around.ieuri.community.domain.comment

import org.around.ieuri.community.domain.shared.CommunityUserException

class NotCommentOwnerException(message: String = "본인의 댓글만 삭제할 수 있습니다.") : CommunityUserException("NOT_COMMENT_OWNER", message)
