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
     * 좋아요를 누른 시점의 `spot` 행. **더 이상 식별자가 아니다**(V54) — 참조 축이
     * [catalogId]로 옮겨 갔고 upsert 단위도 `(member_id, catalog_id)`다.
     *
     * 그럼에도 남기는 이유는 **이관이 잘못됐을 때 되돌릴 유일한 원본**이기 때문이다.
     * 좋아요는 사용자가 만든 데이터라 복구할 곳이 없다.
     */
    @Column(name = "spot_id", nullable = false)
    var spotId: Long,

    /**
     * 소속 카탈로그. **이것이 좋아요의 참조 축이다**(SPEC-0019 R-10).
     *
     * 이전에는 `spot_id`가 언어별이라 일본어로 누른 좋아요가 영어로 바꾸면 사라졌다.
     * 카탈로그는 장소 단위라 언어와 무관하게 하나로 모인다.
     *
     * 연관(`@ManyToOne`)이 아니라 plain 컬럼으로 둔다 — 애그리거트 간 참조는 id로 한다.
     *
     * **`nullable = false`는 이전이 끝난 뒤의 상태를 가리킨다.** 전환 중(V53 적용 후,
     * phase2/V54 적용 전)에는 DB 컬럼이 nullable이다 — 그 구간에는 배정 배치만 이 테이블을
     * 읽고 쓰며, 애플리케이션이 만드는 새 반응은 항상 카탈로그를 해석해 넣는다.
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
