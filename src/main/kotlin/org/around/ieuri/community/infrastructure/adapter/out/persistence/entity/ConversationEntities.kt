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
import org.around.ieuri.common.persistence.DbSchema
import org.around.ieuri.community.domain.vo.ReportReason
import java.time.Instant

// DM JPA 엔티티(SPEC-0017). 다른 도메인 id는 plain 컬럼이다.

/** 1:1 대화. 참여자는 정렬된 쌍(member_low < member_high, DB CHECK)이며 쌍마다 대화는 하나다. */
@Entity
@Table(name = "community_conversation", schema = DbSchema.COMMUNITY)
class ConversationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "member_low", nullable = false)
    var memberLow: Long,

    @Column(name = "member_high", nullable = false)
    var memberHigh: Long,

    /** 정렬 키(최근 활동순). 메시지가 없으면 생성 시각이다. */
    @Column(name = "last_message_at", nullable = false)
    var lastMessageAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,

    /** 각 참여자가 마지막으로 읽은 메시지 id. null은 "아직 아무것도 읽지 않음"이다. */
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

/** DM 메시지. 본문은 매퍼가 암호화해 저장한다(R16) — 검색 대상이 될 수 없다. */
@Entity
@Table(name = "community_message", schema = DbSchema.COMMUNITY)
class MessageEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "conversation_id", nullable = false)
    var conversationId: Long,

    @Column(name = "sender_id", nullable = false)
    var senderId: Long,

    /** base64(iv || ciphertext+tag). 사진만 있는 메시지는 빈 문자열이다. */
    @Column(name = "body_encrypted", nullable = false, columnDefinition = "TEXT")
    var bodyEncrypted: String,

    /** 첨부 사진의 오브젝트 키(POL-042). */
    @Column(name = "photo_object_key", length = 512)
    var photoObjectKey: String? = null,

    /**
     * 탈퇴 회원의 메시지를 가리는 표시(SPEC-0011). null이면 살아 있다.
     * - 도메인 `Message`에는 없다. 읽기 계층이 조회에서 거른다.
     * - 보관 기간 물리 삭제는 `created_at`으로만 고른다(이 값과 무관).
     */
    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,
) {
    @PrePersist
    fun onCreate() {
        if (createdAt == null) createdAt = Instant.now()
    }
}

/**
 * 메시지가 인용한 대상의 스냅샷(SPEC-0026 R14). 대상 종류는 [quoteType] 값으로 구분한다.
 * - [targetId]에는 FK가 없다 — 원본이 삭제돼도 인용은 남는다.
 */
@Entity
@Table(name = "community_message_quote", schema = DbSchema.COMMUNITY)
class MessageQuoteEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "message_id", nullable = false)
    var messageId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "quote_type", nullable = false, length = 30)
    var quoteType: MessageQuoteType,

    @Column(name = "target_id", nullable = false)
    var targetId: Long,

    /** 인용 카드의 제목 줄. 장소 답변이면 장소명이다. */
    @Column(name = "title_snapshot", nullable = false, length = 255)
    var titleSnapshot: String,

    /** base64(iv || ciphertext+tag). 본문과 같이 암호화한다(POL-050). */
    @Column(name = "primary_excerpt_encrypted", nullable = false, columnDefinition = "TEXT")
    var primaryExcerptEncrypted: String,

    @Column(name = "secondary_excerpt_encrypted", columnDefinition = "TEXT")
    var secondaryExcerptEncrypted: String? = null,

    /** 대상에 딸린 부가 식별자. 장소 답변이면 카탈로그 id와 질문 id다. */
    @Column(name = "context_id")
    var contextId: Long? = null,

    @Column(name = "parent_id")
    var parentId: Long? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,
) {
    @PrePersist
    fun onCreate() {
        if (createdAt == null) createdAt = Instant.now()
    }
}

/** 인용 대상의 종류. 값을 늘릴 때는 DB의 CHECK도 함께 넓힌다. */
enum class MessageQuoteType {
    /** 장소 질문의 답변. contextId=카탈로그 id, parentId=질문 id. */
    SPOT_ANSWER,
}

/** DM 신고 한 건. 여러 메시지를 [MessageReportItemEntity]로 묶는다(R15). */
@Entity
@Table(name = "community_message_report", schema = DbSchema.COMMUNITY)
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
 * 신고에 포함된 메시지. 운영자의 열람 범위가 이 표로 한정된다(POL-049).
 * - 중복 판정은 신고자 단위라 reporter_id를 함께 든다.
 */
@Entity
@Table(name = "community_message_report_item", schema = DbSchema.COMMUNITY)
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
