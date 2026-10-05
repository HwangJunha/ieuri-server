package org.around.ieuri.community.domain.comment

import org.around.ieuri.community.domain.shared.CommunityUserException

class CommentBodyRequiredException(message: String = "댓글을 입력해 주세요.") : CommunityUserException("COMMENT_BODY_REQUIRED", message)

class CommentBodyLengthInvalidException(message: String = "댓글은 500자를 넘을 수 없습니다.") : CommunityUserException("COMMENT_BODY_LENGTH_INVALID", message)

class NotCommentOwnerException(message: String = "본인의 댓글만 삭제할 수 있습니다.") : CommunityUserException("NOT_COMMENT_OWNER", message)
