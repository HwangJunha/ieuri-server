package org.around.ieuri.community.application.comment.provided.exception

import org.around.ieuri.community.domain.shared.CommunityUserException

class CommentNotFoundException(message: String = "해당 댓글을 찾을 수 없습니다.") : CommunityUserException("COMMENT_NOT_FOUND", message)
