package org.around.ieuri.community.domain.comment

import org.around.ieuri.community.domain.shared.CommunityUserException

class CommentBodyRequiredException(message: String = "댓글을 입력해 주세요.") : CommunityUserException("COMMENT_BODY_REQUIRED", message)
