package org.around.ieuri.community.application.comment.provided

import org.around.ieuri.community.application.comment.provided.dto.CreateCommentCommand
import org.around.ieuri.community.domain.shared.CommunityUserException
import java.time.Instant

/** 댓글 쓰기. memberId는 인증 컨텍스트에서 온다. 글의 댓글 수를 함께 바꾼다. */
interface CommentCommandUseCase {
    /** 생성된 댓글의 id만 돌려준다. */
    fun createComment(command: CreateCommentCommand, memberId: Long): Long

    fun deleteComment(commentId: Long, memberId: Long)

    /** 탈퇴 정리. 탈퇴 커밋 뒤 리스너가 부르며 새 트랜잭션이다. 한 번의 탈퇴는 모든 애그리게이트에 같은 [deletedAt]을 남긴다. 바뀐 행 수(로그용). */
    fun eraseMemberContent(memberId: Long, deletedAt: Instant): Int
}

class CommentNotFoundException(message: String = "해당 댓글을 찾을 수 없습니다.") : CommunityUserException("COMMENT_NOT_FOUND", message)
