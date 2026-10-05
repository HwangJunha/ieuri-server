package org.around.ieuri.community.application.comment.required

import org.around.ieuri.community.domain.comment.Comment
import java.time.Instant

/** 댓글 저장소. 숨김(신고 누적)과 삭제는 각자 한 열만 바꾸는 조건부 UPDATE다. */
interface CommentRepository {
    /** 삭제되지 않은 댓글만. 없으면 null. */
    fun findById(commentId: Long): Comment?

    fun insert(comment: Comment): Long

    /** 신고 누적 숨김. 보이는 댓글이 이번 호출로 숨겨졌으면 그 글 id, 아니면 null. */
    fun hideIfVisible(commentId: Long): Long?

    /**
     * 삭제 표시. 이번 호출로 삭제됐고 그때 숨겨져 있지 않았으면 true — 그때만 글의 댓글 수에 잡혀 있었다.
     * - 이미 삭제됐거나(동시 삭제) 숨겨진 댓글이면 false.
     */
    fun markDeletedWasCounted(commentId: Long, deletedAt: Instant): Boolean

    /** 탈퇴 정리: 작성 댓글 삭제 표시. 이번에 지운 것 중 수에 잡혀 있던(숨겨지지 않은) 댓글의 글 id를 건마다 돌려준다. */
    fun markDeletedByAuthorCounted(authorId: Long, deletedAt: Instant): List<Long>
}
