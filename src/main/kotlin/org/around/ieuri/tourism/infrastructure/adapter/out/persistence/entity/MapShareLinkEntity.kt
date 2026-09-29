package org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import org.around.ieuri.common.persistence.DbSchema
import java.time.Instant

/**
 * 방문 지도 공유 링크 JPA 엔티티(SPEC-0030 R12~R14). 폐기는 행 삭제가 아니라 [revokedAt]이다.
 * - 회원당 유효 링크 1건은 부분 UNIQUE 인덱스(V78)가 강제한다.
 */
@Entity
@Table(name = "map_share_link", schema = DbSchema.TOUR)
class MapShareLinkEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "member_id", nullable = false)
    var memberId: Long,

    @Column(name = "token", nullable = false, length = 64)
    var token: String,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,

    /** NULL이면 유효, 값이 있으면 폐기됨. */
    @Column(name = "revoked_at")
    var revokedAt: Instant? = null,
) {
    @PrePersist
    fun onCreate() {
        val now = Instant.now()
        if (createdAt == null) createdAt = now
    }
}
