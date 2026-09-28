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

/** 약관 본문 JPA 엔티티(버전 포함). */
@Entity
@Table(name = "terms", schema = DbSchema.AUTH)
class TermsEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "code", nullable = false, length = 50)
    var code: String,

    @Column(name = "title", nullable = false, length = 200)
    var title: String,

    @Column(name = "body", nullable = false, columnDefinition = "text")
    var body: String,

    @Column(name = "version", nullable = false, length = 20)
    var version: String,

    @Column(name = "required", nullable = false)
    var required: Boolean,

    @Column(name = "effective_at", nullable = false)
    var effectiveAt: Instant,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,
) {
    @PrePersist
    fun onCreate() {
        if (createdAt == null) createdAt = Instant.now()
    }
}
