package org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity

import org.around.ieuri.common.persistence.DbSchema
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import java.time.Instant

/** 권한 JPA 엔티티. 인가의 최소 단위를 코드로 식별한다. */
@Entity
@Table(name = "permission", schema = DbSchema.AUTH)
class PermissionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "code", nullable = false, length = 100)
    var code: String,

    @Column(name = "description", length = 255)
    var description: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,
) {
    @PrePersist
    fun onCreate() {
        if (createdAt == null) createdAt = Instant.now()
    }
}
