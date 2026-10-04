package org.around.ieuri.community.infrastructure.adapter.out.persistence.repository

import org.around.ieuri.community.domain.report.ReportTargetType
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

interface PostJpaRepository : JpaRepository<PostEntity, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): PostEntity?

    // 탈퇴 회원 글의 소프트 삭제는 MemberContentSoftDeleteAdapter가 맡는다(SPEC-0011).
}

/** 글의 사진. position 순서가 표시 순서다. */
interface PostPhotoJpaRepository : JpaRepository<PostPhotoEntity, Long> {
    fun findByPostIdOrderByPositionAsc(postId: Long): List<PostPhotoEntity>

    fun deleteByPostId(postId: Long): Long
}

/** 글의 태그. id 순서가 입력 순서다. */
interface PostTagJpaRepository : JpaRepository<PostTagEntity, Long> {
    fun findByPostIdOrderByIdAsc(postId: Long): List<PostTagEntity>

    fun deleteByPostId(postId: Long): Long
}

/** 글 ↔ 카테고리. PK가 post_id다. */
interface PostChipJpaRepository : JpaRepository<PostChipEntity, Long>

/** 글 ↔ 장소. PK가 post_id이며 행이 없을 수 있다. */
interface PostSpotJpaRepository : JpaRepository<PostSpotEntity, Long> {
    /** 사라진 카탈로그를 가리키던 연결 삭제(SPEC-0023). 지운 행 수를 돌려준다. */
    fun deleteByCatalogIdIn(catalogIds: Collection<Long>): Long
}

interface PostHideJpaRepository : JpaRepository<PostHideEntity, Long> {
    fun existsByMemberIdAndPostId(memberId: Long, postId: Long): Boolean

    /** 지운 행 수. 0이면 원래 숨기지 않았다. */
    fun deleteByMemberIdAndPostId(memberId: Long, postId: Long): Long

    /** 탈퇴 회원이 건 숨김 전량 삭제(R17). */
    fun deleteByMemberId(memberId: Long): Long
}

interface CommentJpaRepository : JpaRepository<CommentEntity, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): CommentEntity?

    // 탈퇴 회원 댓글의 소프트 삭제도 MemberContentSoftDeleteAdapter가 맡는다.
}

interface PostLikeJpaRepository : JpaRepository<PostLikeEntity, Long> {
    fun existsByPostIdAndMemberId(postId: Long, memberId: Long): Boolean

    /** 지운 행 수. 0이면 원래 없었다. */
    fun deleteByPostIdAndMemberId(postId: Long, memberId: Long): Long

    /** 탈퇴 회원이 누른 좋아요 전량 삭제(R17). */
    fun deleteByMemberId(memberId: Long): Long
}

interface BlockJpaRepository : JpaRepository<BlockEntity, Long> {
    fun existsByBlockerIdAndBlockedId(blockerId: Long, blockedId: Long): Boolean

    fun deleteByBlockerIdAndBlockedId(blockerId: Long, blockedId: Long): Long

    /** 탈퇴 회원이 걸었거나 회원을 향한 차단 전량 삭제(R17). */
    fun deleteByBlockerIdOrBlockedId(blockerId: Long, blockedId: Long): Long
}

interface ReportJpaRepository : JpaRepository<ReportEntity, Long> {
    fun existsByReporterIdAndTargetTypeAndTargetId(
        reporterId: Long,
        targetType: ReportTargetType,
        targetId: Long,
    ): Boolean

    /** 대상을 신고한 서로 다른 신고자 수(유일 제약 덕분에 행 수와 같다). */
    fun countByTargetTypeAndTargetId(targetType: ReportTargetType, targetId: Long): Long
}
