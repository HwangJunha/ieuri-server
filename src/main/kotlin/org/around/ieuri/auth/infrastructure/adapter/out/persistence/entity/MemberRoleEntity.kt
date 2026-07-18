package org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant

/** 회원↔역할 매핑(다대다) 조인 엔티티. 복합 PK(member_id, role_id). member는 id로만 참조한다. */
@Entity
@Table(name = "member_role")
@IdClass(MemberRoleId::class)
class MemberRoleEntity(
    @Id
    @Column(name = "member_id", nullable = false)
    var memberId: Long,

    @Id
    @Column(name = "role_id", nullable = false)
    var roleId: Long,

    @Column(name = "assigned_at", nullable = false)
    var assignedAt: Instant? = null,
) {
    @PrePersist
    fun onCreate() {
        if (assignedAt == null) assignedAt = Instant.now()
    }
}

/** member_role 복합키. JPA @IdClass 요구사항(no-arg 생성자 + equals/hashCode). */
data class MemberRoleId(
    val memberId: Long = 0,
    val roleId: Long = 0,
) : Serializable
