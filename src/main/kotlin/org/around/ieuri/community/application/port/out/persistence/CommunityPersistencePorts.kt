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

/**
 * 조회수 증가. **애그리게이트를 거치지 않는다** — 읽고 고쳐 쓰면 동시 조회가 서로의 증가를
 * 덮어써 조회수가 누락된다. `view_count = view_count + 1` 한 문장이라 DB가 원자적으로 처리한다.
 *
 * 조회수는 글의 불변식에 관여하지 않는 단순 계수라 이 우회가 정당하다 — 좋아요는 좋아요 행과
 * 집계가 한 트랜잭션에서 맞아야 해서 애그리게이트를 거친다.
 */
interface PostViewCountPort {
    /** 삭제된 글은 올리지 않는다. 대상이 없으면 조용히 지나간다 — 조회수는 실패시킬 값이 아니다. */
    fun increase(postId: Long)
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
 * 차단 쓰기. **조회는 여기 없다** — 피드 필터가 쓰는 양방향 해석은 읽기 경로라
 * CommunityQueryPort.findBlockedMemberIds가 담당한다(한 번의 쿼리로 읽는다).
 */
interface BlockRepositoryPort {
    /** 새로 저장했으면 true, 이미 차단 중이었으면 false(멱등 no-op). */
    fun save(block: Block): Boolean

    fun delete(blockerId: Long, blockedId: Long): Boolean
}

/** 신고 유일성·집계. */
interface ReportRepositoryPort {
    /** 새로 저장했으면 true, 같은 대상을 이미 신고했으면 false. */
    fun save(report: Report): Boolean

    /** 대상을 신고한 **서로 다른** 신고자 수. 같은 사람이 여러 번 신고해도 1이다. */
    fun countDistinctReporters(targetType: ReportTargetType, targetId: Long): Int
}
