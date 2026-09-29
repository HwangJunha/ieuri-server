package org.around.ieuri.community.application.port.out.persistence

import org.around.ieuri.community.domain.aggregate.Block
import org.around.ieuri.community.domain.aggregate.Comment
import org.around.ieuri.community.domain.aggregate.Post
import org.around.ieuri.community.domain.aggregate.PostLike
import org.around.ieuri.community.domain.aggregate.Report
import org.around.ieuri.community.domain.vo.ContentLanguage
import org.around.ieuri.community.domain.vo.ReportTargetType

/**
 * 글 애그리게이트 적재·조회. 전체 상태가 필요한 연산(글 수정)만 쓴다.
 * - 카운터·존재 확인은 더 싼 [PostCounterPort]·[PostModerationPort]를 쓴다.
 */
interface PostRepositoryPort {
    /** 삭제되지 않은 글만. 없으면 null. */
    fun findById(postId: Long): Post?

    fun save(post: Post): Post
}

/**
 * 글의 파생 계수를 애그리게이트를 거치지 않고 원자적으로 증감한다.
 * - 삭제된 글은 갱신하지 않으며, 대상이 없어도 오류가 아니다(0건 갱신).
 */
interface PostCounterPort {
    fun increaseLikeCount(postId: Long)

    /** 0 아래로 내려가지 않는다 — DB의 CHECK 제약과 조건절이 함께 막는다. */
    fun decreaseLikeCount(postId: Long)

    fun increaseCommentCount(postId: Long)

    fun decreaseCommentCount(postId: Long)

    fun increaseViewCount(postId: Long)
}

/** 글 존재 확인과 상태 전환. 애그리게이트 전체를 복원하지 않는다. */
interface PostModerationPort {
    /** 삭제되지 않은 글이 있는지. */
    fun exists(postId: Long): Boolean

    /** 신고 누적에 따른 자동 숨김. 이미 숨겨져 있으면 아무것도 하지 않고 false. */
    fun hide(postId: Long): Boolean
}

interface CommentRepositoryPort {
    fun findById(commentId: Long): Comment?

    fun save(comment: Comment): Comment
}

/** 좋아요 유일성. 멱등 판정을 저장소가 맡고, 호출자는 반환값으로 집계를 올릴지 결정한다. */
interface PostLikeRepositoryPort {
    /** 새로 저장했으면 true, 이미 있었으면 false(멱등 no-op). */
    fun save(like: PostLike): Boolean

    /** 실제로 지웠으면 true, 원래 없었으면 false(멱등 no-op). */
    fun delete(postId: Long, memberId: Long): Boolean
}

/**
 * 한 사용자가 자기 화면에서만 감추는 글 숨김 쓰기.
 * - 신고 누적으로 모두에게 감추는 community_post.hidden과 다른 개념이다.
 */
interface PostHideRepositoryPort {
    /** 새로 숨겼으면 true, 이미 숨겨져 있었으면 false(멱등 no-op). */
    fun save(memberId: Long, postId: Long): Boolean

    /** 실제로 해제했으면 true, 원래 숨기지 않았으면 false(멱등 no-op). */
    fun delete(memberId: Long, postId: Long): Boolean
}

/** 차단 쓰기. 양방향 조회는 CommunityQueryPort.findBlockedMemberIds가 담당한다. */
interface BlockRepositoryPort {
    /** 새로 저장했으면 true, 이미 차단 중이었으면 false(멱등 no-op). */
    fun save(block: Block): Boolean

    fun delete(blockerId: Long, blockedId: Long): Boolean
}

/** 사라진 장소를 가리키는 글-장소 연결 정리(SPEC-0023 R11). 컨텍스트 간 FK가 없어 애플리케이션이 맡는다. */
interface PostSpotCleanupPort {
    /** 지운 연결 행 수. 한 장소를 여러 글이 참조하므로 단건이 아니다. */
    fun deleteByCatalogIds(catalogIds: List<Long>): Long
}

/** 카탈로그가 해소되는 언어의 커뮤니티 사본(SPEC-0029). 원본은 tourism에 있다. */
interface SpotCatalogLanguagePort {
    /**
     * 표 전체를 [entries]로 맞추고 반영 후 총 행 수를 돌려준다.
     * - 여기 없는 조합은 지운다 — 빈 목록이면 전부 지운다.
     */
    fun replaceAll(entries: List<CatalogLanguageEntry>): Int
}

/** 한 카탈로그가 한 언어에서 해소된다는 사실. 이벤트가 싣는 쌍과 같은 모양이다. */
data class CatalogLanguageEntry(
    val catalogId: Long,
    val language: ContentLanguage,
)

/** 신고 유일성·집계. */
interface ReportRepositoryPort {
    /** 새로 저장했으면 true, 같은 대상을 이미 신고했으면 false. */
    fun save(report: Report): Boolean

    /** 대상을 신고한 서로 다른 신고자 수. 같은 사람이 여러 번 신고해도 1이다. */
    fun countDistinctReporters(targetType: ReportTargetType, targetId: Long): Int
}
