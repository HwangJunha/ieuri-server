package org.around.ieuri.community.application.comment.provided

import org.around.ieuri.community.application.comment.provided.dto.CreateCommentCommand
import org.around.ieuri.community.domain.shared.CommunityUserException

/** 댓글 쓰기. memberId는 인증 컨텍스트에서 온다. 글의 댓글 수를 함께 바꾼다. */
interface CommentCommandUseCase {
    /** 생성된 댓글의 id만 돌려준다. */
    fun createComment(command: CreateCommentCommand, memberId: Long): Long

    fun deleteComment(commentId: Long, memberId: Long)
}

class CommentNotFoundException(message: String = "해당 댓글을 찾을 수 없습니다.") : CommunityUserException("COMMENT_NOT_FOUND", message)
