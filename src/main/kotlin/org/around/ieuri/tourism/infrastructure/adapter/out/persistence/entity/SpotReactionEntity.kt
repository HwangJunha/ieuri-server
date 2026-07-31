package org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.ReactionType
import java.io.Serializable
import java.time.Instant

/**
 * 좋아요/싫어요 JPA 엔티티. 도메인 모델(SpotReaction)과 분리되며 어댑터가 매핑한다.
 * 복합 PK (member_id, content_id, language) = upsert 단위(AC-12/13). 관광 데이터는 저장하지 않는다.
 */
@Entity
@Table(name = "spot_reaction")
@IdClass(SpotReactionId::class)
class SpotReactionEntity(
    @Id
    @Column(name = "member_id", nullable = false)
    var memberId: Long,

    @Id
    @Column(name = "content_id", nullable = false, length = 32)
    var contentId: String,

    @Id
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

/**
 * 복합 PK 식별자 클래스. @IdClass 요건: 기본 생성자 + Serializable + equals/hashCode.
 * data class가 이 모두를 제공한다(all-open이 필요 없어 domain vo 참조만 갖는다).
 */
data class SpotReactionId(
    val memberId: Long = 0,
    val contentId: String = "",
    val language: DomainLocale = DomainLocale.EN,
) : Serializable
