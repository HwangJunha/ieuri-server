package org.around.ieuri.auth.infrastructure.adapter.out.persistence.repository

import org.around.ieuri.auth.domain.member.Provider
import org.around.ieuri.auth.domain.member.VerificationPurpose
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.EmailVerificationEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.MemberAccessLogEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.MemberAuditLogEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.MemberEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.MemberLocationAccessLogEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.MemberProfileEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.MemberTermsAgreementEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.PasswordResetEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.TermsEntity
import org.springframework.data.domain.Limit
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
}

interface MemberTermsAgreementJpaRepository : JpaRepository<MemberTermsAgreementEntity, Long> {
    fun deleteByMemberId(memberId: Long)

    /** 회원의 약관별 현재 상태. `(member_id, terms_id)` 유일 제약(V38)으로 약관당 한 행이다. */
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

    /** 쿨다운 판정용. 소진 여부를 가리지 않는다 — 제한 대상은 발송이지 코드 사용이 아니다. */
    fun findFirstByMemberIdOrderByCreatedAtDesc(memberId: Long): PasswordResetEntity?

    /** 탈퇴 시 정리(FK 자식). 없으면 member 삭제가 제약에 걸려 탈퇴가 영구히 막힌다. */
    fun deleteByMemberId(memberId: Long)
}

interface MemberAuditLogJpaRepository : JpaRepository<MemberAuditLogEntity, Long> {

    /**
     * 보관 기간이 지난 기록을 최대 [limit]건 고른다(SPEC-0032 R22).
     * - 벌크 삭제에는 LIMIT이 없어 대상을 먼저 골라 회차 단위로 지운다.
     */
    fun findByCreatedAtBefore(cutoff: Instant, limit: Limit): List<MemberAuditLogEntity>

    fun deleteByIdIn(ids: Collection<Long>): Long
}

/** 접속기록(안전성 확보조치 기준 제8조). 보관 정리는 `MemberAuditLogJpaRepository`와 같은 형태다(1년). */
interface MemberAccessLogJpaRepository : JpaRepository<MemberAccessLogEntity, Long> {

    fun findByCreatedAtBefore(cutoff: Instant, limit: Limit): List<MemberAccessLogEntity>

    fun deleteByIdIn(ids: Collection<Long>): Long
}

/** 위치 조회 감사 기록(SPEC-0032 R19). 보관 정리는 `MemberAuditLogJpaRepository`와 같은 형태다(6개월). */
interface MemberLocationAccessLogJpaRepository : JpaRepository<MemberLocationAccessLogEntity, Long> {

    fun findByCreatedAtBefore(cutoff: Instant, limit: Limit): List<MemberLocationAccessLogEntity>

    fun deleteByIdIn(ids: Collection<Long>): Long
}
