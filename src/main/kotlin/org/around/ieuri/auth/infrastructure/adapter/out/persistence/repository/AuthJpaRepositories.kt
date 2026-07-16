package org.around.ieuri.auth.infrastructure.adapter.out.persistence.repository

import org.around.ieuri.auth.domain.vo.Provider
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.EmailVerificationEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.MemberAuditLogEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.MemberEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.MemberProfileEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.MemberTermsAgreementEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.TermsEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface MemberJpaRepository : JpaRepository<MemberEntity, Long> {
    fun existsByProviderAndProviderUserId(provider: Provider, providerUserId: String): Boolean
}

interface MemberProfileJpaRepository : JpaRepository<MemberProfileEntity, Long>

interface MemberTermsAgreementJpaRepository : JpaRepository<MemberTermsAgreementEntity, Long>

interface TermsJpaRepository : JpaRepository<TermsEntity, Long> {
    fun findByEffectiveAtLessThanEqual(now: Instant): List<TermsEntity>
}

interface EmailVerificationJpaRepository : JpaRepository<EmailVerificationEntity, Long> {
    fun findByPublicId(publicId: UUID): EmailVerificationEntity?

    @Modifying
    @Query(
        """
        UPDATE EmailVerificationEntity e
        SET e.consumed = true
        WHERE e.memberId = :memberId AND e.consumed = false AND e.verifiedAt IS NULL
        """,
    )
    fun consumeActiveByMemberId(@Param("memberId") memberId: Long): Int

    @Query("SELECT MAX(e.createdAt) FROM EmailVerificationEntity e WHERE e.memberId = :memberId")
    fun findLatestCreatedAtByMemberId(@Param("memberId") memberId: Long): Instant?
}

interface MemberAuditLogJpaRepository : JpaRepository<MemberAuditLogEntity, Long>
