package org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** 이메일 검증 챌린지 JPA 엔티티. 코드는 code_hash로만 저장한다. */
@Entity
@Table(name = "email_verification")
class EmailVerificationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "public_id", nullable = false)
    var publicId: UUID,

    @Column(name = "member_id", nullable = false)
    var memberId: Long,

    @Column(name = "code_hash", nullable = false, length = 128)
    var codeHash: String,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

    @Column(name = "attempts", nullable = false)
    var attempts: Int,

    @Column(name = "max_attempts", nullable = false)
    var maxAttempts: Int,

    @Column(name = "consumed", nullable = false)
    var consumed: Boolean,

    @Column(name = "verified_at")
    var verifiedAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,
) {
    @PrePersist
    fun onCreate() {
        if (createdAt == null) createdAt = Instant.now()
    }
}
