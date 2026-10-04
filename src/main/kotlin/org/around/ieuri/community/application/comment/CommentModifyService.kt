package org.around.ieuri.community.application.comment

import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.community.application.comment.provided.CommentCommandUseCase
import org.around.ieuri.community.application.comment.provided.CommentNotFoundException
import org.around.ieuri.community.application.comment.provided.dto.CreateCommentCommand
import org.around.ieuri.community.application.comment.required.CommentRepository
import org.around.ieuri.community.application.port.out.client.ClockPort
import org.around.ieuri.community.application.post.provided.exception.PostNotFoundException
import org.around.ieuri.community.application.post.required.PostRepository
import org.around.ieuri.community.domain.comment.Comment
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 댓글 생성·삭제. 대댓글도 글의 댓글 수에 포함된다. */
@Service
class CommentModifyService(
    private val commentRepository: CommentRepository,
    private val postRepository: PostRepository,
    private val clockPort: ClockPort,
) : CommentCommandUseCase {

    @Transactional(transactionManager = ExposedConfig.EXPOSED_TX)
    override fun createComment(command: CreateCommentCommand, memberId: Long): Long {
        if (!postRepository.exists(command.postId)) throw PostNotFoundException()

        // 다른 글의 댓글을 부모로 지정하면 없는 댓글로 본다.
        val parent = command.parentCommentId?.let { parentId ->
            commentRepository.findById(parentId)?.takeIf { it.postId == command.postId } ?: throw CommentNotFoundException()
        }

        val comment = Comment.write(command.postId, memberId, command.body, parent)

        val commentId = commentRepository.insert(comment)
        postRepository.increaseCommentCount(command.postId)
        return commentId
    }

    @Transactional(transactionManager = ExposedConfig.EXPOSED_TX)
    override fun deleteComment(commentId: Long, memberId: Long) {
        require(commentId > 0) { "commentId는 양수여야 합니다." }
        val comment = commentRepository.findById(commentId) ?: throw CommentNotFoundException()

        val deleted = comment.delete(memberId, clockPort.now())

        if (commentRepository.markDeletedWasCounted(commentId, requireNotNull(deleted.deletedAt))) {
            postRepository.decreaseCommentCount(comment.postId)
        }
    }
}
