package org.around.ieuri.community.infrastructure.adapter.out.persistence.repository

import org.around.ieuri.community.domain.vo.ReportTargetType
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.BlockEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.CommentEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.PostChipEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.PostEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.PostHideEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.PostLikeEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.PostPhotoEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.PostSpotEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.PostTagEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.ReportEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 파생 쿼리만 쓴다. @Query 문자열은 두지 않으며(정의가 두 곳으로 갈리고 타입 검사가 안 된다),
 * 복잡한 조회는 QueryDSL 어댑터가 맡는다.
 */

interface PostJpaRepository : JpaRepository<PostEntity, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): PostEntity?

    // 탈퇴 회원의 글은 **소프트 삭제한다**(SPEC-0011 R17) — 행 삭제하면 거기 달린 남의
    // 댓글·좋아요가 CASCADE로 함께 사라져 남의 화면이 깨진다. 벌크 UPDATE라 파생 쿼리로
    // 표현할 수 없어 QueryDSL 어댑터가 맡는다(MemberContentSoftDeleteAdapter).
}

/** 글의 사진. position 순서가 표시 순서다. */
interface PostPhotoJpaRepository : JpaRepository<PostPhotoEntity, Long> {
    fun findByPostIdOrderByPositionAsc(postId: Long): List<PostPhotoEntity>

    /** 사진 교체 시 기존 행을 지운다. 지운 행 수는 쓰지 않는다. */
    fun deleteByPostId(postId: Long): Long
}

/** 글의 태그. 입력 순서를 id 순서가 보존한다. */
interface PostTagJpaRepository : JpaRepository<PostTagEntity, Long> {
    fun findByPostIdOrderByIdAsc(postId: Long): List<PostTagEntity>

    /** 태그 교체 시 기존 행을 지운다. 지운 행 수는 쓰지 않는다. */
    fun deleteByPostId(postId: Long): Long
}

/** 글 ↔ 카테고리. PK가 post_id라 findById가 곧 글 단위 조회다. */
interface PostChipJpaRepository : JpaRepository<PostChipEntity, Long>

/** 글 ↔ 장소. 장소는 선택이라 행이 없을 수 있다. */
interface PostSpotJpaRepository : JpaRepository<PostSpotEntity, Long> {
    /**
     * 사라진 카탈로그를 가리키던 연결 삭제(SPEC-0023 R11). 지운 행 수를 돌려준다 —
     * 한 장소를 여러 글이 참조하므로 단건이 아니다.
     */
    fun deleteByCatalogIdIn(catalogIds: Collection<Long>): Long
}

/** 글 숨김. 유일 제약이 멱등을 판정하므로 exists→save 순으로 쓴다. */
interface PostHideJpaRepository : JpaRepository<PostHideEntity, Long> {
    fun existsByMemberIdAndPostId(memberId: Long, postId: Long): Boolean

    /** 지운 행 수를 돌려준다 — 0이면 원래 숨기지 않았다는 뜻이고 멱등 no-op 판정의 근거다. */
    fun deleteByMemberIdAndPostId(memberId: Long, postId: Long): Long

    /** 탈퇴 회원이 건 숨김 전량 삭제(R17). */
    fun deleteByMemberId(memberId: Long): Long
}

interface CommentJpaRepository : JpaRepository<CommentEntity, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): CommentEntity?

    // 탈퇴 회원의 댓글도 소프트 삭제다(R17) — 위와 같은 이유이며 같은 어댑터가 맡는다.
}

interface PostLikeJpaRepository : JpaRepository<PostLikeEntity, Long> {
    fun existsByPostIdAndMemberId(postId: Long, memberId: Long): Boolean

    /** 지운 행 수를 돌려준다 — 0이면 원래 없었다는 뜻이고 멱등 no-op 판정의 근거다. */
    fun deleteByPostIdAndMemberId(postId: Long, memberId: Long): Long

    /** 탈퇴 회원이 누른 좋아요 전량 삭제(R17). */
    fun deleteByMemberId(memberId: Long): Long
}

interface BlockJpaRepository : JpaRepository<BlockEntity, Long> {
    fun existsByBlockerIdAndBlockedId(blockerId: Long, blockedId: Long): Boolean

    fun deleteByBlockerIdAndBlockedId(blockerId: Long, blockedId: Long): Long

    /** 탈퇴 회원이 걸었거나 회원을 향한 차단 전량 삭제(R17). 양방향을 함께 지운다. */
    fun deleteByBlockerIdOrBlockedId(blockerId: Long, blockedId: Long): Long
}

interface ReportJpaRepository : JpaRepository<ReportEntity, Long> {
    fun existsByReporterIdAndTargetTypeAndTargetId(
        reporterId: Long,
        targetType: ReportTargetType,
        targetId: Long,
    ): Boolean

    /**
     * 대상을 신고한 **서로 다른** 신고자 수. `(reporterId, targetType, targetId)` 유일 제약
     * 덕분에 행 수가 곧 신고자 수다 — 같은 사람이 여러 번 신고할 수 없다.
     */
    fun countByTargetTypeAndTargetId(targetType: ReportTargetType, targetId: Long): Long
}
