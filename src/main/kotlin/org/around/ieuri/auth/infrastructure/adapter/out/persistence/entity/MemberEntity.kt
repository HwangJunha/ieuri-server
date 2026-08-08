package org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import org.around.ieuri.auth.domain.vo.MemberStatus
import org.around.ieuri.auth.domain.vo.Provider
import java.time.Instant

/**
 * 회원 JPA 엔티티. 도메인 모델(Member)과 분리되며 매퍼로만 오간다.
 * 이메일은 email_encrypted에 암호문으로만 저장한다.
 */
@Entity
@Table(name = "member")
class MemberEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    var provider: Provider,

    @Column(name = "provider_user_id", nullable = false, length = 255)
    var providerUserId: String,

    @Column(name = "email_encrypted", nullable = false, length = 512)
    var emailEncrypted: String,

    @Column(name = "email_verified", nullable = false)
    var emailVerified: Boolean,

    @Column(name = "password_hash", length = 255)
    var passwordHash: String? = null,

    /** 탈퇴 시 provider 연결 해제용 refresh token(암호문). Kakao·EMAIL 회원은 null이다. */
    @Column(name = "provider_refresh_token_encrypted", length = 1024)
    var providerRefreshTokenEncrypted: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: MemberStatus,

    @Column(name = "age_verified", nullable = false)
    var ageVerified: Boolean,

    @Column(name = "suspended_at")
    var suspendedAt: Instant? = null,

    @Column(name = "suspended_until")
    var suspendedUntil: Instant? = null,

    @Column(name = "suspension_reason", length = 500)
    var suspensionReason: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null,
) {
    @PrePersist
    fun onCreate() {
        val now = Instant.now()
        if (createdAt == null) createdAt = now
        updatedAt = now
    }

    @PreUpdate
    fun onUpdate() {
        updatedAt = Instant.now()
    }
}
