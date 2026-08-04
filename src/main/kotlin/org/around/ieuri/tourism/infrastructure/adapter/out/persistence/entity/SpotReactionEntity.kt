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
import org.around.ieuri.tourism.domain.vo.ReactionType
import java.time.Instant

/**
 * 좋아요/싫어요 JPA 엔티티. 도메인 모델(SpotReaction)과 분리되며 `mapper` 패키지가 변환한다.
 * PK는 surrogate id(JPA 친화적: 단일 @Id, 연관·merge 단순). 자연키 (member_id, spot_id)는
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
     * 부모 `spot` 행의 surrogate PK. **이제 이것이 유일한 관광지 식별자다**(V20) — 자연키
     * (content_id·language)가 자식에서 제거되어 upsert 단위도 `(member_id, spot_id)`가 됐다.
     *
     * 연관(`@ManyToOne`)이 아니라 plain 컬럼으로 둔다. `spot`을 객체로 로드할 일이 없어 연관을 걸면
     * 지연 로딩 프록시와 N+1 위험만 늘고, 애그리거트 간 참조는 id로 한다는 규약과도 맞는다.
     */
    @Column(name = "spot_id", nullable = false)
    var spotId: Long,

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
