package org.around.ieuri.community.application.service

import org.around.ieuri.community.application.command.CreateCommentCommand
import org.around.ieuri.community.application.command.DeleteCommentCommand
import org.around.ieuri.community.application.exception.CommentBodyLengthInvalidException
import org.around.ieuri.community.application.exception.CommentBodyRequiredException
import org.around.ieuri.community.application.exception.CommentNotFoundException
import org.around.ieuri.community.application.exception.NotCommentOwnerException
import org.around.ieuri.community.application.exception.PostNotFoundException
import org.around.ieuri.community.application.port.`in`.command.CommentCommandUseCase
import org.around.ieuri.community.application.port.out.client.ClockPort
import org.around.ieuri.community.application.port.out.persistence.CommentRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.PostCounterPort
import org.around.ieuri.community.application.port.out.persistence.PostModerationPort
import org.around.ieuri.community.domain.aggregate.Comment
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 댓글 생성·삭제. 글의 commentCount를 같은 트랜잭션에서 함께 갱신한다.
 * - 대댓글도 수에 포함된다. 이미 숨겨진 댓글을 지울 때는 수를 내리지 않는다.
 */
@Service
class CommentCommandService(
    private val commentRepositoryPort: CommentRepositoryPort,
    private val postCounterPort: PostCounterPort,
    private val postModerationPort: PostModerationPort,
    private val clockPort: ClockPort,
) : CommentCommandUseCase {

    @Transactional
    override fun createComment(command: CreateCommentCommand, memberId: Long): Long {
        // 빈 본문과 길이 초과를 다른 코드로 내려고 도메인보다 먼저 판정한다.
        val body = command.body.trim()
        if (body.isEmpty()) throw CommentBodyRequiredException()
        if (body.length > Comment.BODY_MAX_LENGTH) throw CommentBodyLengthInvalidException()

        if (!postModerationPort.exists(command.postId)) throw PostNotFoundException()

        val saved = commentRepositoryPort.save(
            Comment.create(
                postId = command.postId,
                authorId = memberId,
                body = body,
                parentCommentId = command.parentCommentId?.let { resolveReplyParentId(it, command.postId) },
            ),
        )
        postCounterPort.increaseCommentCount(command.postId)

        return requireNotNull(saved.id) { "저장된 댓글에는 id가 있어야 한다." }
    }

    /**
     * 답글이 붙을 실제 부모. 대댓글에 답글을 달면 거부하지 않고 그 최상위 부모에 붙인다(2단계 유지).
     * - 다른 글의 댓글을 부모로 지정하면 CommentNotFoundException이다.
     */
    private fun resolveReplyParentId(parentCommentId: Long, postId: Long): Long {
        val parent = commentRepositoryPort.findById(parentCommentId)
            ?: throw CommentNotFoundException()
        if (parent.postId != postId) throw CommentNotFoundException()
        return Comment.replyParentIdFor(parent)
    }

    @Transactional
    override fun deleteComment(command: DeleteCommentCommand, memberId: Long) {
        val comment = commentRepositoryPort.findById(command.commentId)
            ?: throw CommentNotFoundException()
        // 이미 숨겨진 댓글은 commentCount에서 이미 빠져 있다.
        val countedBeforeDelete = !comment.hidden
        if (!comment.deleteBy(memberId, clockPort.now())) throw NotCommentOwnerException()
        commentRepositoryPort.save(comment)

        // 0 아래로 내려가지 않는 것은 어댑터의 조건절이 보장한다.
        if (countedBeforeDelete) postCounterPort.decreaseCommentCount(comment.postId)
    }
}
