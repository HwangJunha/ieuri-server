package org.around.ieuri.auth.infrastructure.adapter.out.persistence.repository

import org.around.ieuri.auth.domain.vo.Provider
import org.around.ieuri.auth.domain.vo.VerificationPurpose
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.EmailVerificationEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.MemberAuditLogEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.MemberEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.MemberProfileEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.MemberTermsAgreementEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.PasswordResetEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.TermsEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface MemberJpaRepository : JpaRepository<MemberEntity, Long> {
    fun existsByProviderAndProviderUserId(provider: Provider, providerUserId: String): Boolean

    fun findByProviderAndProviderUserId(provider: Provider, providerUserId: String): MemberEntity?

    /** `(provider, email)` 중복 판정(SPEC-0024 R5). 가입처럼 본인이 아직 없을 때. */
    fun existsByProviderAndEmailIdentifier(provider: Provider, emailIdentifier: String): Boolean

    /** 위와 같되 본인을 제외한다 — 이메일 변경·인증 주소 재입력은 자기 주소가 걸리면 안 된다. */
    fun existsByProviderAndEmailIdentifierAndIdNot(
        provider: Provider,
        emailIdentifier: String,
        id: Long,
    ): Boolean
}

interface MemberProfileJpaRepository : JpaRepository<MemberProfileEntity, Long> {
    fun findByMemberId(memberId: Long): MemberProfileEntity?
    fun deleteByMemberId(memberId: Long)

    /** 닉네임 중복 판정. 본인은 제외한다 — 같은 값 재제출은 중복이 아니다(SPEC-0011 R5). */
    fun existsByNicknameAndMemberIdNot(nickname: String, memberId: Long): Boolean
}

interface MemberTermsAgreementJpaRepository : JpaRepository<MemberTermsAgreementEntity, Long> {
    fun deleteByMemberId(memberId: Long)

    /**
     * 회원의 약관별 **현재 상태**. `(member_id, terms_id)` 유일 제약(V38) 덕분에 약관 하나에
     * 한 행이며, 여기서 고를 것이 없다 — 이력은 member_audit_log가 갖는다.
     */
    fun findByMemberId(memberId: Long): List<MemberTermsAgreementEntity>

    /** 재동의·철회 시 갱신할 관리 엔티티. 없으면 신규 동의다. */
    fun findByMemberIdAndTermsId(memberId: Long, termsId: Long): MemberTermsAgreementEntity?
}

interface TermsJpaRepository : JpaRepository<TermsEntity, Long> {
    fun findByEffectiveAtLessThanEqual(now: Instant): List<TermsEntity>
}

interface EmailVerificationJpaRepository : JpaRepository<EmailVerificationEntity, Long> {
    fun findByPublicId(publicId: UUID): EmailVerificationEntity?

    /** 용도별 최근 챌린지. 쿨다운을 가입 검증과 이메일 변경으로 나눠 센다(SPEC-0011 R7). */
    fun findFirstByMemberIdAndPurposeOrderByCreatedAtDesc(
        memberId: Long,
        purpose: VerificationPurpose,
    ): EmailVerificationEntity?

    fun deleteByMemberId(memberId: Long)
}

interface PasswordResetJpaRepository : JpaRepository<PasswordResetEntity, Long> {
    fun findByPublicId(publicId: UUID): PasswordResetEntity?

    fun findFirstByMemberIdAndConsumedFalseOrderByCreatedAtDesc(memberId: Long): PasswordResetEntity?

    /** 탈퇴 시 정리(FK 자식). 없으면 member 삭제가 제약에 걸려 탈퇴가 영구히 막힌다. */
    fun deleteByMemberId(memberId: Long)
}

interface MemberAuditLogJpaRepository : JpaRepository<MemberAuditLogEntity, Long>
