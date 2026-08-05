package org.around.ieuri.community.application.port.out.persistence

import org.around.ieuri.community.domain.aggregate.Block
import org.around.ieuri.community.domain.aggregate.Comment
import org.around.ieuri.community.domain.aggregate.Post
import org.around.ieuri.community.domain.aggregate.PostLike
import org.around.ieuri.community.domain.aggregate.Report
import org.around.ieuri.community.domain.vo.ReportTargetType

/** 글 적재·조회. 삭제는 소프트 삭제이므로 [save]가 삭제 상태도 함께 반영한다. */
interface PostRepositoryPort {
    /** 삭제되지 않은 글만. 없으면 null. */
    fun findById(postId: Long): Post?

    fun save(post: Post): Post
}

interface CommentRepositoryPort {
    fun findById(commentId: Long): Comment?

    fun save(comment: Comment): Comment
}

/**
 * 좋아요 유일성. 멱등 판정을 저장소가 맡는다 — 이미 있으면 [save]가 false를 돌려주고
 * 없는 것을 [delete]해도 false다. 호출자는 그 값으로 집계를 올릴지 결정한다.
 */
interface PostLikeRepositoryPort {
    /** 새로 저장했으면 true, 이미 있었으면 false(멱등 no-op). */
    fun save(like: PostLike): Boolean

    /** 실제로 지웠으면 true, 원래 없었으면 false(멱등 no-op). */
    fun delete(postId: Long, memberId: Long): Boolean
}

/**
 * 차단 관계. [findRelatedMemberIds]가 양방향 해석의 핵심이다 — 네 조회 경로가 모두 이걸 쓴다.
 */
interface BlockRepositoryPort {
    /** 새로 저장했으면 true, 이미 차단 중이었으면 false(멱등 no-op). */
    fun save(block: Block): Boolean

    fun delete(blockerId: Long, blockedId: Long): Boolean

    /**
     * [memberId]와 차단 관계에 있는 모든 상대의 id. **양방향이다** — 내가 차단한 사람과
     * 나를 차단한 사람을 합집합으로 돌려준다.
     */
    fun findRelatedMemberIds(memberId: Long): Set<Long>
}

/** 신고 유일성·집계. */
interface ReportRepositoryPort {
    /** 새로 저장했으면 true, 같은 대상을 이미 신고했으면 false. */
    fun save(report: Report): Boolean

    /** 대상을 신고한 **서로 다른** 신고자 수. 같은 사람이 여러 번 신고해도 1이다. */
    fun countDistinctReporters(targetType: ReportTargetType, targetId: Long): Int
}
