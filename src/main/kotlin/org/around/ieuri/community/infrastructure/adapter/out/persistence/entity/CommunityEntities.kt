package org.around.ieuri.community.infrastructure.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import org.around.ieuri.community.domain.vo.ContentLanguage
import org.around.ieuri.community.domain.vo.TargetCategory
import org.around.ieuri.community.domain.vo.ReportReason
import org.around.ieuri.community.domain.vo.ReportTargetType
import java.time.Instant

/**
 * community 도메인 JPA 엔티티. 도메인 모델과 분리되며 `mapper` 패키지가 변환한다.
 *
 * 다른 도메인을 가리키는 id(spot_id·author_id·member_id)는 plain 컬럼이다 — 물리 FK도
 * 연관(@ManyToOne)도 두지 않는다. 애그리게이트 간 참조는 id로 한다는 규약이자, 도메인이
 * 분리될 때 따라가야 하는 형태다.
 */
@Entity
@Table(name = "community_post")
class PostEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "author_id", nullable = false)
    var authorId: Long,

    /** 길이 판정은 도메인이 소유한다 — 컬럼은 넉넉히 잡는다(이모지의 코드포인트 수 문제). */
    @Column(name = "title", nullable = false, length = 100)
    var title: String,

    /** TEXT 컬럼이다 — 길이 판정은 도메인이 소유한다(이모지의 코드포인트 수 문제). */
    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    var body: String,

    @Column(name = "like_count", nullable = false)
    var likeCount: Int = 0,

    @Column(name = "comment_count", nullable = false)
    var commentCount: Int = 0,

    /** 조회수. 증가는 PostViewCountAdapter의 원자적 UPDATE가 하며 여기서 고치지 않는다. */
    @Column(name = "view_count", nullable = false)
    var viewCount: Int = 0,

    @Column(name = "hidden", nullable = false)
    var hidden: Boolean = false,

    /** 수정된 적이 있는지. 클라이언트의 "수정됨" 표시에 쓴다. */
    @Column(name = "edited", nullable = false)
    var edited: Boolean = false,

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,

    // **자식 컬렉션을 두지 않는다.** 사진·태그·카테고리·장소 어느 것도 여기서 참조하지 않는다.
    //
    // 부모가 컬렉션을 들면 글을 여러 건 읽을 때마다 자식 조회가 건수만큼 따라붙고(N+1),
    // 한 건만 읽어도 매퍼가 접근하는 순간 쿼리가 나간다. 연관은 자식 쪽 @ManyToOne만 남기고
    // (실무에서 @OneToMany보다 @ManyToOne을 선호하는 이유다), 적재·조회는 각 리포지토리와
    // QueryDSL이 명시적으로 한다 — 언제 무엇을 읽는지가 코드에 드러난다.
    //
    // 삭제는 DB의 ON DELETE CASCADE가 받는다(JPA cascade와 별개로 동작한다).

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
@Table(name = "community_post_photo")
class PostPhotoEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    /**
     * 연관이 아니라 plain 컬럼이다. 부모를 프록시로 들고 있을 이유가 없고, 이 편이
     * 댓글·좋아요와 같은 모양이라 도메인 전체가 한 가지 규칙을 따른다.
     */
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
@Table(name = "community_comment")
class CommentEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "post_id", nullable = false)
    var postId: Long,

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
@Table(name = "community_post_like")
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

@Entity
@Table(name = "community_block")
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
@Table(name = "community_report")
class ReportEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "reporter_id", nullable = false)
    var reporterId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 10)
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

/**
 * 상단 카테고리 칩 구성. 앱마다 다른 구성을 데이터로 관리한다.
 * **표시 문구 컬럼이 없다** — 앱이 category를 언어별로 매핑한다.
 */
@Entity
@Table(name = "community_chip")
class CommunityChipEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "app_key", nullable = false, length = 50)
    var appKey: String,

    /** 카테고리 가용 집합이 언어마다 달라 구성도 언어별로 나뉜다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 2)
    var language: ContentLanguage,

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    var category: TargetCategory,

    @Column(name = "position", nullable = false)
    var position: Short,

    /** 순서를 그대로 둔 채 잠시 내릴 수 있게 한다. */
    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = true,

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

/**
 * 글 태그. 표시는 [name](유저 입력 원문), 매칭은 [normalized](소문자)로 한다.
 */
@Entity
@Table(name = "community_post_tag")
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

/**
 * 글 ↔ 카테고리. 글당 하나이며 PK가 post_id다.
 *
 * 카테고리를 spot에서 유도하지 않고 여기 저장하는 이유는 **spot이 선택이기 때문**이다 —
 * 유도하면 장소 없이 쓴 글은 상단 칩을 눌렀을 때 전부 사라진다.
 */
@Entity
@Table(name = "community_post_chip")
class PostChipEntity(
    /** 글의 id가 그대로 PK다. 연관 대신 plain 컬럼으로 둔 이유는 PostEntity의 주석 참고. */
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

/** 글 ↔ 장소. 글당 최대 하나이며 없을 수 있다. spot_id는 tourism 소유라 물리 FK가 없다. */
@Entity
@Table(name = "community_post_spot")
class PostSpotEntity(
    @Id
    @Column(name = "post_id")
    var postId: Long,

    @Column(name = "spot_id", nullable = false)
    var spotId: Long,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,
) {
    @PrePersist
    fun onCreate() {
        if (createdAt == null) createdAt = Instant.now()
    }
}
