package org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import org.around.ieuri.common.persistence.DbSchema
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * 위치 조회 감사 기록 JPA 엔티티(SPEC-0032 R19·R20).
 *
 * **좌표 컬럼이 없는 것이 이 테이블의 요지다** — 누가·어떤 연산을·언제 셋만 남긴다.
 * [payload]에도 좌표를 담지 않는다(POL-045).
 */
@Entity
@Table(name = "member_location_access_log", schema = DbSchema.AUTH)
class MemberLocationAccessLogEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "member_id", nullable = false)
    var memberId: Long,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    var payload: String,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,
) {
    @PrePersist
    fun onCreate() {
        if (createdAt == null) createdAt = Instant.now()
    }
}
