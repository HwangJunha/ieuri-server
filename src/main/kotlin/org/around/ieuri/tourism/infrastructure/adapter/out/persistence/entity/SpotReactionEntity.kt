package org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity

import org.around.ieuri.common.persistence.DbSchema
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
import org.around.ieuri.tourism.domain.vo.ReactionType
import java.time.Instant

/**
 * 좋아요/싫어요 JPA 엔티티. 도메인 모델(SpotReaction)과 분리되며 `mapper` 패키지가 변환한다.
 * PK는 surrogate id(JPA 친화적: 단일 @Id, 연관·merge 단순). 자연키 (member_id, catalog_id)는
 * DB UNIQUE(uq_spot_reaction)로 강제되며 upsert(재평가 덮어쓰기) 단위다(AC-12/13). 관광 데이터는 저장하지 않는다.
 */
@Entity
@Table(name = "spot_reaction", schema = DbSchema.TOUR)
class SpotReactionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "member_id", nullable = false)
    var memberId: Long,

    /**
     * 소속 카탈로그. **이것이 좋아요의 참조 축이다**(SPEC-0019 R-10).
     *
     * 이전에는 `spot_id`가 언어별이라 일본어로 누른 좋아요가 영어로 바꾸면 사라졌다.
     * 카탈로그는 장소 단위라 언어와 무관하게 하나로 모인다.
     *
     * 연관(`@ManyToOne`)이 아니라 plain 컬럼으로 둔다 — 애그리거트 간 참조는 id로 한다.
     */
    @Column(name = "catalog_id", nullable = false)
    var catalogId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "reaction", nullable = false, length = 10)
    var reaction: ReactionType,

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
