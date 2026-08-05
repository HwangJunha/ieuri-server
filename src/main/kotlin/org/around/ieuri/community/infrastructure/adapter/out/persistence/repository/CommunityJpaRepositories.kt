package org.around.ieuri.community.infrastructure.adapter.out.persistence.repository

import org.around.ieuri.community.domain.vo.ReportTargetType
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.BlockEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.CommentEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.PostChipEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.PostEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.PostLikeEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.PostSpotEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.ReportEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 파생 쿼리만 쓴다. @Query 문자열은 두지 않으며(정의가 두 곳으로 갈리고 타입 검사가 안 된다),
 * 복잡한 조회는 QueryDSL 어댑터가 맡는다.
 */

interface PostJpaRepository : JpaRepository<PostEntity, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): PostEntity?
}

/** 글 ↔ 카테고리. PK가 post_id라 findById가 곧 글 단위 조회다. */
interface PostChipJpaRepository : JpaRepository<PostChipEntity, Long>

/** 글 ↔ 장소. 장소는 선택이라 행이 없을 수 있다. */
interface PostSpotJpaRepository : JpaRepository<PostSpotEntity, Long>

interface CommentJpaRepository : JpaRepository<CommentEntity, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): CommentEntity?
}

interface PostLikeJpaRepository : JpaRepository<PostLikeEntity, Long> {
    fun existsByPostIdAndMemberId(postId: Long, memberId: Long): Boolean

    /** 지운 행 수를 돌려준다 — 0이면 원래 없었다는 뜻이고 멱등 no-op 판정의 근거다. */
    fun deleteByPostIdAndMemberId(postId: Long, memberId: Long): Long
}

interface BlockJpaRepository : JpaRepository<BlockEntity, Long> {
    fun existsByBlockerIdAndBlockedId(blockerId: Long, blockedId: Long): Boolean

    fun deleteByBlockerIdAndBlockedId(blockerId: Long, blockedId: Long): Long
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
