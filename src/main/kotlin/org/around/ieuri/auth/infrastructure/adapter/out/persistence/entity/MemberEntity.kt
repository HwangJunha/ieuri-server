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
import org.around.ieuri.auth.domain.member.MemberStatus
import org.around.ieuri.auth.domain.member.Provider
import org.around.ieuri.common.persistence.DbSchema
import java.time.Instant

/** 회원 JPA 엔티티. 이메일은 email_encrypted에 암호문으로만 저장한다. */
@Entity
@Table(name = "member", schema = DbSchema.AUTH)
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

    /**
     * `(provider, email)` 유니크 판정용 파생 값(SPEC-0024 R5). 정규화된 이메일의 결정론적 HMAC.
     * - `email_encrypted`와 반드시 함께 갱신한다. 어긋나면 제약이 엉뚱한 주소를 막는다.
     */
    @Column(name = "email_identifier", nullable = false, length = 64)
    var emailIdentifier: String,

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
