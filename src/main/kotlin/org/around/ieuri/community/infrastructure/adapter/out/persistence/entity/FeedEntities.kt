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
import org.around.ieuri.community.domain.vo.ReportReason
import java.time.Instant

/**
 * 피드·DM JPA 엔티티(SPEC-0017). 도메인 모델과 분리되며 `mapper` 패키지가 변환한다.
 *
 * 다른 도메인을 가리키는 id(catalog_id·author_id·sender_id)는 plain 컬럼이다 — 물리 FK도
 * 연관(@ManyToOne)도 두지 않는다(CommunityEntities와 같은 규약).
 */
@Entity
@Table(name = "community_feed_item")
class FeedItemEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "author_id", nullable = false)
    var authorId: Long,

    /** **nullable이 아니다** — 피드에서 장소 연결은 필수다(R1). tourism 소유의 카탈로그 id다. */
    @Column(name = "catalog_id", nullable = false)
    var catalogId: Long,

    /** TEXT 컬럼이다 — 길이 판정은 도메인이 소유한다(이모지의 코드포인트 수 문제). */
    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    var body: String,

    @Column(name = "like_count", nullable = false)
    var likeCount: Int = 0,

    @Column(name = "hidden", nullable = false)
    var hidden: Boolean = false,

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,

    // 사진은 자식 컬렉션으로 두지 않는다 — 이유는 PostEntity의 주석 참고.

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

/** 피드 사진. object_key가 전역 유일해 애그리게이트 간 재사용을 막는다(AC3-3). */
@Entity
@Table(name = "community_feed_item_photo")
class FeedItemPhotoEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "feed_item_id", nullable = false)
    var feedItemId: Long,

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
@Table(name = "community_feed_item_like")
class FeedItemLikeEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "feed_item_id", nullable = false)
    var feedItemId: Long,

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

/**
 * 1:1 대화. 참여자를 **정렬된 쌍**으로 들고 있다 — member_low < member_high가 DB의 CHECK로
 * 강제되며, 그것이 "같은 쌍에 대화는 하나"를 유일 제약으로 판정할 수 있는 근거다(AC7-2).
 *
 * **시작된 피드를 들지 않는다** — 방은 하나인데 피드는 여러 개라 방에 고정하면 틀린 맥락이
 * 된다. 인용은 메시지에 붙는다([MessageEntity.feedItemId]).
 */
@Entity
@Table(name = "community_conversation")
class ConversationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "member_low", nullable = false)
    var memberLow: Long,

    @Column(name = "member_high", nullable = false)
    var memberHigh: Long,

    /** 정렬 키(최근 활동순). 메시지가 없으면 생성 시각 그대로다 — 빈 대화가 존재할 수 있다. */
    @Column(name = "last_message_at", nullable = false)
    var lastMessageAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,

    /**
     * 각 참여자가 마지막으로 읽은 메시지 id. **null은 "아직 아무것도 읽지 않았다"이며 0이
     * 아니다** — 0으로 두면 "id 0까지 읽었다"와 구분되지 않는다.
     */
    @Column(name = "last_read_message_id_low")
    var lastReadMessageIdLow: Long? = null,

    @Column(name = "last_read_message_id_high")
    var lastReadMessageIdHigh: Long? = null,
) {
    @PrePersist
    fun onCreate() {
        val now = Instant.now()
        if (createdAt == null) createdAt = now
        if (lastMessageAt == null) lastMessageAt = now
    }
}

/**
 * DM 메시지. **본문은 암호화되어 저장된다**(R16) — 매퍼가 암복호화를 하며 이 컬럼에 평문이
 * 들어가는 경로는 없다.
 *
 * 암호화 컬럼이라 LIKE·전문 검색의 대상이 아니다. 대화 검색은 비범위다.
 */
@Entity
@Table(name = "community_message")
class MessageEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "conversation_id", nullable = false)
    var conversationId: Long,

    @Column(name = "sender_id", nullable = false)
    var senderId: Long,

    /**
     * base64(iv || ciphertext+tag). 평문 길이 판정은 도메인이 소유한다.
     * **첨부만 있는 메시지는 빈 문자열이다** — null과 구분해 복호화 실패와 헷갈리지 않게 한다.
     */
    @Column(name = "body_encrypted", nullable = false, columnDefinition = "TEXT")
    var bodyEncrypted: String,

    /** 인용한 피드. 피드가 지워지면 null이 되고 메시지는 남는다(ON DELETE SET NULL). */
    @Column(name = "feed_item_id")
    var feedItemId: Long? = null,

    /** 첨부 사진의 오브젝트 키. 글·피드와 같은 업로드 경로를 쓴다(POL-042). */
    @Column(name = "photo_object_key", length = 512)
    var photoObjectKey: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,
) {
    @PrePersist
    fun onCreate() {
        if (createdAt == null) createdAt = Instant.now()
    }
}

/** DM 신고 한 건. 여러 메시지를 [MessageReportItemEntity]로 묶는다(R15). */
@Entity
@Table(name = "community_message_report")
class MessageReportEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "reporter_id", nullable = false)
    var reporterId: Long,

    @Column(name = "conversation_id", nullable = false)
    var conversationId: Long,

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
 * 신고에 포함된 메시지. **운영자의 열람 범위가 이 표로 한정된다**(POL-049).
 *
 * reporter_id를 함께 들고 있는 이유는 중복 판정이 신고자 단위이기 때문이다 — report_id로는
 * 다른 신고 건의 중복을 볼 수 없다(AC15-6).
 */
@Entity
@Table(name = "community_message_report_item")
class MessageReportItemEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "report_id", nullable = false)
    var reportId: Long,

    @Column(name = "message_id", nullable = false)
    var messageId: Long,

    @Column(name = "reporter_id", nullable = false)
    var reporterId: Long,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,
) {
    @PrePersist
    fun onCreate() {
        if (createdAt == null) createdAt = Instant.now()
    }
}
