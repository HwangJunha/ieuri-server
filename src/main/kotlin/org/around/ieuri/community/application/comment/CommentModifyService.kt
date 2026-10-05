package org.around.ieuri.community.application.comment

import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.community.application.comment.provided.CommentCommandUseCase
import org.around.ieuri.community.application.comment.provided.CommentNotFoundException
import org.around.ieuri.community.application.comment.provided.dto.CreateCommentCommand
import org.around.ieuri.community.application.comment.required.CommentRepository
import org.around.ieuri.community.application.post.provided.exception.PostNotFoundException
import org.around.ieuri.community.application.post.required.PostRepository
import org.around.ieuri.community.application.shared.required.ClockPort
import org.around.ieuri.community.domain.comment.Comment
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** 댓글 생성·삭제. 대댓글도 글의 댓글 수에 포함된다. */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX)
class CommentModifyService(
    private val commentRepository: CommentRepository,
    private val postRepository: PostRepository,
    private val clockPort: ClockPort,
) : CommentCommandUseCase {

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

    override fun deleteComment(commentId: Long, memberId: Long) {
        require(commentId > 0) { "commentId는 양수여야 합니다." }
        val comment = commentRepository.findById(commentId) ?: throw CommentNotFoundException()

        val deleted = comment.delete(memberId, clockPort.now())

        if (commentRepository.markDeletedWasCounted(commentId, requireNotNull(deleted.deletedAt))) {
            postRepository.decreaseCommentCount(comment.postId)
        }
    }

    /** 작성 댓글을 사용자 삭제와 같은 경로(도메인 판정 → 수에 잡혀 있던 것만 글의 댓글 수를 내림)로 하나씩 지운다. */
    @Transactional(transactionManager = ExposedConfig.EXPOSED_TX, propagation = Propagation.REQUIRES_NEW)
    override fun eraseMemberContent(memberId: Long, deletedAt: Instant): Int {
        val commentIds = commentRepository.findIdsByAuthor(memberId)
        commentIds.forEach { commentId ->
            val comment = commentRepository.findById(commentId) ?: return@forEach
            val deleted = comment.delete(memberId, deletedAt)
            if (commentRepository.markDeletedWasCounted(commentId, requireNotNull(deleted.deletedAt))) {
                postRepository.decreaseCommentCount(comment.postId)
            }
        }
        return commentIds.size
    }
}
