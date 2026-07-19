package org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable

/** 역할↔권한 매핑(다대다) 조인 엔티티. 복합 PK(role_id, permission_id). */
@Entity
@Table(name = "role_permission")
@IdClass(RolePermissionId::class)
class RolePermissionEntity(
    @Id
    @Column(name = "role_id", nullable = false)
    var roleId: Long,

    @Id
    @Column(name = "permission_id", nullable = false)
    var permissionId: Long,
)

/** role_permission 복합키. JPA @IdClass 요구사항(no-arg 생성자 + equals/hashCode). */
data class RolePermissionId(
    val roleId: Long = 0,
    val permissionId: Long = 0,
) : Serializable
