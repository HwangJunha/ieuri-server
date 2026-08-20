package org.around.ieuri.community.application.port.out.persistence

import org.around.ieuri.community.domain.aggregate.Block
import org.around.ieuri.community.domain.aggregate.Comment
import org.around.ieuri.community.domain.aggregate.Post
import org.around.ieuri.community.domain.aggregate.PostLike
import org.around.ieuri.community.domain.aggregate.Report
import org.around.ieuri.community.domain.vo.ContentLanguage
import org.around.ieuri.community.domain.vo.ReportTargetType

/**
 * 글 애그리게이트 적재·조회. **전체 상태가 필요한 연산만 이 포트를 쓴다** — 지금은 글 수정뿐이다.
 *
 * [findById]는 태그·사진까지 갖춘 완전한 애그리게이트를 복원하므로 여러 번의 조회가 필요하다.
 * 카운터 증감이나 존재 확인처럼 일부만 필요한 연산은 [PostCounterPort]·[PostModerationPort]를
 * 쓴다 — 그쪽이 훨씬 싸고, 카운터는 동시성까지 더 정확하다.
 */
interface PostRepositoryPort {
    /** 삭제되지 않은 글만. 없으면 null. */
    fun findById(postId: Long): Post?

    fun save(post: Post): Post
}

/**
 * 글의 파생 계수 증감. **애그리게이트를 거치지 않는다.**
 *
 * 읽고 고쳐 쓰면 동시 요청이 서로의 증가를 덮어써 계수가 실제보다 작아진다 — 두 사람이 같은
 * 글에 동시에 좋아요를 누르면 하나가 사라지는 식이다. `count = count + 1` 한 문장은 DB가
 * 원자적으로 처리해 그 경합이 아예 없다.
 *
 * 계수는 글의 불변식에 관여하지 않는 단순 숫자라 이 우회가 정당하다. **유일성 판정은 여전히
 * 저장소의 제약이 맡는다** — 이 포트는 판정 결과를 숫자에 반영할 뿐이다.
 *
 * 삭제된 글은 갱신하지 않으며, 대상이 없어도 오류가 아니다(0건 갱신).
 */
interface PostCounterPort {
    fun increaseLikeCount(postId: Long)

    /** 0 아래로 내려가지 않는다 — DB의 CHECK 제약과 조건절이 함께 막는다. */
    fun decreaseLikeCount(postId: Long)

    fun increaseCommentCount(postId: Long)

    fun decreaseCommentCount(postId: Long)

    fun increaseViewCount(postId: Long)
}

/**
 * 존재 확인과 상태 전환. 애그리게이트 전체를 복원할 이유가 없는 연산들이다.
 *
 * 신고의 대상 확인은 "있는가"만 물으면 되는데 애그리게이트를 복원하면 태그·사진까지 읽는다.
 */
interface PostModerationPort {
    /** 삭제되지 않은 글이 있는지. */
    fun exists(postId: Long): Boolean

    /**
     * 신고 누적에 따른 자동 숨김. **이미 숨겨져 있으면 아무것도 하지 않고 false**를 돌려준다 —
     * 호출자가 그 값으로 중복 처리를 건너뛴다.
     */
    fun hide(postId: Long): Boolean
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
 * 글 숨김 쓰기. **community_post.hidden과 다른 개념이다** — 그쪽은 신고 누적으로 모두에게
 * 감춰진 것이고, 여기는 한 사용자가 자기 화면에서만 감춘 것이다.
 *
 * 좋아요·차단과 같이 멱등 판정을 저장소의 유일 제약이 맡는다.
 */
interface PostHideRepositoryPort {
    /** 새로 숨겼으면 true, 이미 숨겨져 있었으면 false(멱등 no-op). */
    fun save(memberId: Long, postId: Long): Boolean

    /** 실제로 해제했으면 true, 원래 숨기지 않았으면 false(멱등 no-op). */
    fun delete(memberId: Long, postId: Long): Boolean
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

/**
 * 사라진 장소를 가리키는 글-장소 연결 정리(SPEC-0023 R11).
 *
 * `community_post_spot.catalog_id`는 NOT NULL이면서 컨텍스트 간 FK가 없다(R4). 그래서
 * 카탈로그가 사라져도 DB가 막아 주지 않고 매칭 불가능한 행이 조용히 남는다 — 그 정리를
 * 애플리케이션 계층이 맡는다.
 */
interface PostSpotCleanupPort {
    /** 지운 연결 행 수. 한 장소를 여러 글이 참조하므로 단건이 아니다. */
    fun deleteByCatalogIds(catalogIds: List<Long>): Long
}

/**
 * 카탈로그가 해소되는 언어의 커뮤니티 사본(SPEC-0029 R-15·R-23). 글·질문 목록이 활성 언어로
 * 거를 때 보는 표이며, 원본은 tourism에 있다.
 */
interface SpotCatalogLanguagePort {
    /**
     * 표 **전체를** [entries]로 맞춘다 — 여기 없는 조합은 지운다.
     *
     * 배정 배치의 노출 갱신이 정본을 통째로 보내므로 델타를 적용하지 않는다. 델타 방식은
     * 빈 표를 채울 수 없다(전량 재적재 직후에는 뒤집힌 행이 없어 델타가 빈다).
     *
     * 반영 후의 총 행 수를 돌려준다.
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

    /** 대상을 신고한 **서로 다른** 신고자 수. 같은 사람이 여러 번 신고해도 1이다. */
    fun countDistinctReporters(targetType: ReportTargetType, targetId: Long): Int
}
