package org.around.ieuri.community.infrastructure.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import org.around.ieuri.common.persistence.DbSchema
import org.around.ieuri.community.domain.vo.ContentLanguage
import org.around.ieuri.community.domain.vo.ReportReason
import org.around.ieuri.community.domain.vo.ReportTargetType
import org.around.ieuri.community.domain.vo.TargetCategory
import java.io.Serializable
import java.time.Instant

/** 커뮤니티 글. 다른 도메인·애그리게이트 id는 plain 컬럼이다(FK·연관 없음). */
@Entity
@Table(name = "community_post", schema = DbSchema.COMMUNITY)
class PostEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "author_id", nullable = false)
    var authorId: Long,

    /** 길이 판정은 도메인이 소유한다(컬럼은 넉넉히). */
    @Column(name = "title", nullable = false, length = 100)
    var title: String,

    /** 길이 판정은 도메인이 소유한다. */
    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    var body: String,

    /** 세 카운터는 PostCounterAdapter의 원자적 UPDATE로만 바꾼다 — 엔티티로 고치지 않는다. */
    @Column(name = "like_count", nullable = false)
    var likeCount: Int = 0,

    @Column(name = "comment_count", nullable = false)
    var commentCount: Int = 0,

    @Column(name = "view_count", nullable = false)
    var viewCount: Int = 0,

    @Column(name = "hidden", nullable = false)
    var hidden: Boolean = false,

    /** 수정된 적이 있는지("수정됨" 표시). */
    @Column(name = "edited", nullable = false)
    var edited: Boolean = false,

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,

    // 자식 컬렉션을 두지 않는다. 자식 행 삭제는 DB의 ON DELETE CASCADE가 받는다.

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null,
) {
    @PrePersist
    fun onCreate() {
        val now = Instant.now()
        if (createdAt == null) createdAt = now
        updatedAt = now
    }

    @PreUpdate
    fun onUpdate() {
        updatedAt = Instant.now()
    }
}

@Entity
@Table(name = "community_post_photo", schema = DbSchema.COMMUNITY)
class PostPhotoEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "post_id", nullable = false)
    var postId: Long,

    @Column(name = "object_key", nullable = false, length = 512)
    var objectKey: String,

    @Column(name = "position", nullable = false)
    var position: Short,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,
) {
    @PrePersist
    fun onCreate() {
        if (createdAt == null) createdAt = Instant.now()
    }
}

@Entity
@Table(name = "community_comment", schema = DbSchema.COMMUNITY)
class CommentEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "post_id", nullable = false)
    var postId: Long,

    /** 최상위 댓글이면 null. 깊이 2단계 제한은 도메인이 지킨다(DB는 자기 참조만 막는다). */
    @Column(name = "parent_comment_id")
    var parentCommentId: Long? = null,

    @Column(name = "author_id", nullable = false)
    var authorId: Long,

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    var body: String,

    @Column(name = "hidden", nullable = false)
    var hidden: Boolean = false,

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null,
) {
    @PrePersist
    fun onCreate() {
        val now = Instant.now()
        if (createdAt == null) createdAt = now
        updatedAt = now
    }

    @PreUpdate
    fun onUpdate() {
        updatedAt = Instant.now()
    }
}

@Entity
@Table(name = "community_post_like", schema = DbSchema.COMMUNITY)
class PostLikeEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "post_id", nullable = false)
    var postId: Long,

    @Column(name = "member_id", nullable = false)
    var memberId: Long,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,
) {
    @PrePersist
    fun onCreate() {
        if (createdAt == null) createdAt = Instant.now()
    }
}

/** 사용자가 개별 글을 숨긴 기록. community_post.hidden(신고 누적)과 다른 개념이다. */
@Entity
@Table(name = "community_post_hide", schema = DbSchema.COMMUNITY)
class PostHideEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "member_id", nullable = false)
    var memberId: Long,

    @Column(name = "post_id", nullable = false)
    var postId: Long,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,
) {
    @PrePersist
    fun onCreate() {
        if (createdAt == null) createdAt = Instant.now()
    }
}

@Entity
@Table(name = "community_block", schema = DbSchema.COMMUNITY)
class BlockEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "blocker_id", nullable = false)
    var blockerId: Long,

    @Column(name = "blocked_id", nullable = false)
    var blockedId: Long,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,
) {
    @PrePersist
    fun onCreate() {
        if (createdAt == null) createdAt = Instant.now()
    }
}

@Entity
@Table(name = "community_report", schema = DbSchema.COMMUNITY)
class ReportEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "reporter_id", nullable = false)
    var reporterId: Long,

    /** 길이는 가장 긴 열거값을 담아야 한다 — 열거값을 더할 때 함께 확인한다(INSERT에서만 터진다). */
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30)
    var targetType: ReportTargetType,

    @Column(name = "target_id", nullable = false)
    var targetId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 30)
    var reason: ReportReason,

    /** 상세 사유는 로그·에러 메시지에 싣지 않는다(POL-013). */
    @Column(name = "detail", columnDefinition = "TEXT")
    var detail: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,
) {
    @PrePersist
    fun onCreate() {
        if (createdAt == null) createdAt = Instant.now()
    }
}

/** 글 태그. 표시는 [name](입력 원문), 매칭은 [normalized](소문자)로 한다. */
@Entity
@Table(name = "community_post_tag", schema = DbSchema.COMMUNITY)
class PostTagEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "post_id", nullable = false)
    var postId: Long,

    @Column(name = "name", nullable = false, length = 30)
    var name: String,

    @Column(name = "normalized", nullable = false, length = 30)
    var normalized: String,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,
) {
    @PrePersist
    fun onCreate() {
        if (createdAt == null) createdAt = Instant.now()
    }
}

/** 글 ↔ 카테고리. 글당 하나이며 PK가 post_id다. 장소가 없는 글에도 있다. */
@Entity
@Table(name = "community_post_chip", schema = DbSchema.COMMUNITY)
class PostChipEntity(
    @Id
    @Column(name = "post_id")
    var postId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    var category: TargetCategory,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,
) {
    @PrePersist
    fun onCreate() {
        if (createdAt == null) createdAt = Instant.now()
    }
}

/** 글 ↔ 장소. 글당 최대 하나이며 없을 수 있다. catalog_id는 tourism 소유라 물리 FK가 없다. */
@Entity
@Table(name = "community_post_spot", schema = DbSchema.COMMUNITY)
class PostSpotEntity(
    @Id
    @Column(name = "post_id")
    var postId: Long,

    @Column(name = "catalog_id", nullable = false)
    var catalogId: Long,

    /** 글이 붙은 장소의 좌표(map_x=경도). */
    @Column(name = "map_x", nullable = false)
    var mapX: Double,

    @Column(name = "map_y", nullable = false)
    var mapY: Double,

    // location(geography)은 매핑하지 않는다 — 트리거가 map_x/map_y에서 파생시키며,
    // 매핑하면 ddl-auto=validate가 기동을 막는다.

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,
) {
    @PrePersist
    fun onCreate() {
        if (createdAt == null) createdAt = Instant.now()
    }
}

/**
 * 카탈로그가 해소되는 언어의 커뮤니티 사본(SPEC-0029). 원본은 tourism의
 * `spot_catalog_translation`이며, `SpotCatalogLanguagesChanged` 구독이 이 표를 맞춘다.
 */
@Entity
@Table(name = "community_spot_catalog_language", schema = DbSchema.COMMUNITY)
@IdClass(SpotCatalogLanguageId::class)
class SpotCatalogLanguageEntity(
    @Id
    @Column(name = "catalog_id")
    var catalogId: Long,

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "language", length = 2)
    var language: ContentLanguage,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,
) {
    @PrePersist
    fun onCreate() {
        if (createdAt == null) createdAt = Instant.now()
    }
}

/** community_spot_catalog_language 복합키(@IdClass). */
data class SpotCatalogLanguageId(
    var catalogId: Long = 0,
    var language: ContentLanguage = ContentLanguage.EN,
) : Serializable
