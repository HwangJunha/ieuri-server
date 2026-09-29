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
import java.time.Instant

/** 장소 질문(SPEC-0026). id는 plain 컬럼이며, 답변·답글 삭제 종속은 DB의 ON DELETE CASCADE가 받는다. */
@Entity
@Table(name = "community_spot_question", schema = DbSchema.COMMUNITY)
class SpotQuestionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    /** 언어와 무관한 장소 식별자(FK 없음). */
    @Column(name = "catalog_id", nullable = false)
    var catalogId: Long,

    @Column(name = "author_id", nullable = false)
    var authorId: Long,

    /** 길이 판정은 도메인이 소유한다. */
    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    var body: String,

    @Column(name = "resolved", nullable = false)
    var resolved: Boolean = false,

    /** 숨겨진 답변을 제외한 수. 증감은 SpotQuestionCounterAdapter의 원자적 UPDATE가 한다. */
    @Column(name = "answer_count", nullable = false)
    var answerCount: Int = 0,

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
@Table(name = "community_spot_answer", schema = DbSchema.COMMUNITY)
class SpotAnswerEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "question_id", nullable = false)
    var questionId: Long,

    /** 질문에서 복사한 값. */
    @Column(name = "catalog_id", nullable = false)
    var catalogId: Long,

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

/** 답글. 부모 답글 컬럼이 없다 — 1단계만 가능하다(R7). */
@Entity
@Table(name = "community_spot_answer_reply", schema = DbSchema.COMMUNITY)
class SpotAnswerReplyEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "answer_id", nullable = false)
    var answerId: Long,

    @Column(name = "catalog_id", nullable = false)
    var catalogId: Long,

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

/** 사진의 소유자 종류. [SpotQaPhotoEntity.ownerId]의 의미를 정한다. */
enum class SpotQaPhotoOwnerType {
    QUESTION,
    ANSWER,
    REPLY,
}

/**
 * 질문·답변·답글의 첨부 사진. 셋이 한 표를 쓴다.
 * - FK가 없다 — 부모 삭제 시 사진 행은 영속 어댑터가 직접 지워야 한다.
 */
@Entity
@Table(name = "community_spot_qa_photo", schema = DbSchema.COMMUNITY)
class SpotQaPhotoEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 10)
    var ownerType: SpotQaPhotoOwnerType,

    @Column(name = "owner_id", nullable = false)
    var ownerId: Long,

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
