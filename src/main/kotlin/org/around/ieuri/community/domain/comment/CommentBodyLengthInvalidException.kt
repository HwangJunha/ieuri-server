package org.around.ieuri.community.domain.comment

import org.around.ieuri.community.domain.shared.CommunityUserException

class CommentBodyLengthInvalidException(message: String = "댓글은 500자를 넘을 수 없습니다.") : CommunityUserException("COMMENT_BODY_LENGTH_INVALID", message)
