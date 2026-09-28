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

/** 약관 동의 이력 JPA 엔티티. append-only(수정/삭제하지 않음). */
@Entity
@Table(name = "member_terms_agreement", schema = DbSchema.AUTH)
class MemberTermsAgreementEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "member_id", nullable = false)
    var memberId: Long,

    @Column(name = "terms_id", nullable = false)
    var termsId: Long,

    @Column(name = "terms_code", nullable = false, length = 50)
    var termsCode: String,

    @Column(name = "terms_version", nullable = false, length = 20)
    var termsVersion: String,

    @Column(name = "agreed", nullable = false)
    var agreed: Boolean,

    @Column(name = "agreed_at", nullable = false)
    var agreedAt: Instant? = null,
) {
    @PrePersist
    fun onCreate() {
        if (agreedAt == null) agreedAt = Instant.now()
    }
}
