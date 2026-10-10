package org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import org.around.ieuri.auth.domain.member.VerificationPurpose
import org.around.ieuri.common.persistence.DbSchema
import java.time.Instant
import java.util.UUID

/** 이메일 검증 챌린지 JPA 엔티티. 코드는 code_hash로만 저장한다. */
@Entity
@Table(name = "email_verification", schema = DbSchema.AUTH)
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

    /** 가입 검증인지 이메일 변경인지(SPEC-0011 R7). 이름으로 저장한다 — 순서에 의존하지 않는다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 20)
    var purpose: VerificationPurpose = VerificationPurpose.REGISTRATION,

    /** 이메일 변경 시 확정할 새 주소의 암호문(POL-012). 가입 검증에는 null. */
    @Column(name = "target_email_encrypted", length = 512)
    var targetEmailEncrypted: String? = null,
) {
    @PrePersist
    fun onCreate() {
        if (createdAt == null) createdAt = Instant.now()
    }
}
