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
 * 댓글 생성·삭제. 글의 commentCount를 같은 트랜잭션에서 함께 갱신한다 — 매 조회마다 세지 않는다.
 *
 * 숨겨진 댓글은 그 수에 포함되지 않으므로, 삭제 시에도 **이미 숨겨져 있었다면 내리지 않는다**.
 * 숨김 시점에 이미 한 번 내렸기 때문이며, 두 번 내리면 숫자가 실제보다 작아진다.
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
        // 계약이 빈 본문과 길이 초과를 서로 다른 코드로 구분하므로 도메인보다 먼저 판정한다.
        val body = command.body.trim()
        if (body.isEmpty()) throw CommentBodyRequiredException()
        if (body.length > Comment.BODY_MAX_LENGTH) throw CommentBodyLengthInvalidException()

        // 존재 확인만 필요하다 — 애그리게이트를 복원하면 태그·사진까지 읽는다.
        if (!postModerationPort.exists(command.postId)) throw PostNotFoundException()

        val saved = commentRepositoryPort.save(
            Comment.create(postId = command.postId, authorId = memberId, body = body),
        )
        postCounterPort.increaseCommentCount(command.postId)

        return requireNotNull(saved.id) { "저장된 댓글에는 id가 있어야 한다." }
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
