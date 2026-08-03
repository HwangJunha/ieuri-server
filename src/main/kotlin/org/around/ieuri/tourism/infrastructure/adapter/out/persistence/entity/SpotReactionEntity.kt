package org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity

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
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.ReactionType
import java.time.Instant

/**
 * 좋아요/싫어요 JPA 엔티티. 도메인 모델(SpotReaction)과 분리되며 어댑터가 매핑한다.
 * PK는 surrogate id(JPA 친화적: 단일 @Id, 연관·merge 단순). 자연키 (member_id, content_id, language)는
 * DB UNIQUE(uq_spot_reaction)로 강제되며 upsert(재평가 덮어쓰기) 단위다(AC-12/13). 관광 데이터는 저장하지 않는다.
 */
@Entity
@Table(name = "spot_reaction")
class SpotReactionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "member_id", nullable = false)
    var memberId: Long,

    /**
     * 부모 `spot` 행의 surrogate PK(V19). **참조 무결성과 CASCADE 전용이며 도메인은 이 값을 모른다** —
     * `SpotReaction`에 대응 필드가 없고, 조회·업서트 판정은 여전히 자연키로 한다.
     *
     * 연관(`@ManyToOne`)이 아니라 **plain 컬럼으로 둔다.** 이 값을 읽는 코드가 없어 연관을 걸면
     * 지연 로딩 프록시와 N+1 위험만 늘고 얻는 것이 없다. 애그리거트 간 참조는 id로 한다는
     * 프로젝트 규약과도 맞는다.
     */
    @Column(name = "spot_id", nullable = false)
    var spotId: Long,

    @Column(name = "content_id", nullable = false, length = 32)
    var contentId: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 2)
    var language: DomainLocale,

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
