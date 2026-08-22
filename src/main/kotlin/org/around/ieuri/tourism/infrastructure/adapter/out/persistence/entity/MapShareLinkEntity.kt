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
 * 방문 지도 공유 링크 JPA 엔티티(SPEC-0030 R12~R14).
 *
 * **폐기를 행 삭제가 아니라 [revokedAt]으로 표현한다** — 삭제하면 "폐기된 링크"와 "처음부터
 * 없던 토큰"을 서버가 구분하지 못한다(응답을 같게 하는 것과는 다른 문제다).
 *
 * 회원당 유효한 링크는 하나이며, 그 제약은 부분 UNIQUE 인덱스가 강제한다(V78) — JPA로는
 * 표현할 수 없어 발급이 `ON CONFLICT`를 쓰는 전용 어댑터에 남는 이유다.
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
