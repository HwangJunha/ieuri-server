package org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity

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
 * 접속기록 JPA 엔티티(안전성 확보조치 기준 제8조): 계정·접속일시·접속지·수행업무.
 * - payload가 없다. 항목을 컬럼으로 고정해 다른 것이 섞여 들지 않게 한다.
 */
@Entity
@Table(name = "member_access_log", schema = DbSchema.AUTH)
class MemberAccessLogEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "member_id", nullable = false)
    var memberId: Long,

    @Column(name = "operation", nullable = false, length = 200)
    var operation: String,

    /** 프록시가 주지 않으면 null이다 — 모르는 것을 채워 넣지 않는다. */
    @Column(name = "ip_address", length = 45)
    var ipAddress: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,
) {
    @PrePersist
    fun onCreate() {
        if (createdAt == null) createdAt = Instant.now()
    }
}
