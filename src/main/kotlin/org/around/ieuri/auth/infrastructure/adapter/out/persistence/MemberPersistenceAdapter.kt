package org.around.ieuri.auth.infrastructure.adapter.out.persistence

import org.around.ieuri.auth.application.exception.MemberNotFoundException
import org.around.ieuri.auth.application.port.out.persistence.EmailAuthentication
import org.around.ieuri.auth.application.port.out.persistence.MemberAuthentication
import org.around.ieuri.auth.application.port.out.persistence.MemberRepositoryPort
import org.around.ieuri.auth.application.port.out.security.EmailEncryptorPort
import org.around.ieuri.auth.application.port.out.security.LoginIdentifierProtectorPort
import org.around.ieuri.auth.domain.aggregate.Member
import org.around.ieuri.auth.domain.vo.Email
import org.around.ieuri.auth.domain.vo.Provider
import org.around.ieuri.auth.domain.vo.ProviderUserId
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.mapper.toDomain
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.mapper.toNewEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.mapper.toNewProfileEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.repository.EmailVerificationJpaRepository
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.repository.MemberJpaRepository
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.repository.MemberProfileJpaRepository
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.repository.MemberRoleJpaRepository
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.repository.MemberTermsAgreementJpaRepository
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.repository.PasswordResetJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** 회원 쓰기 포트 구현. 이메일은 암호화해 저장하고, 수정은 managed 엔티티를 고쳐 dirty-check에 맡긴다. */
@Component
class MemberPersistenceAdapter(
    private val memberRepository: MemberJpaRepository,
    private val profileRepository: MemberProfileJpaRepository,
    private val agreementRepository: MemberTermsAgreementJpaRepository,
    private val emailVerificationRepository: EmailVerificationJpaRepository,
    private val memberRoleRepository: MemberRoleJpaRepository,
    private val passwordResetRepository: PasswordResetJpaRepository,
    private val encryptor: EmailEncryptorPort,
    private val identifierProtector: LoginIdentifierProtectorPort,
) : MemberRepositoryPort {

    override fun existsByProviderAndProviderUserId(provider: Provider, providerUserId: ProviderUserId): Boolean = memberRepository.existsByProviderAndProviderUserId(provider, providerUserId.value)

    override fun existsByProviderAndEmailIdentifier(
        provider: Provider,
        emailIdentifier: String,
        excludingMemberId: Long?,
    ): Boolean = if (excludingMemberId == null) {
        memberRepository.existsByProviderAndEmailIdentifier(provider, emailIdentifier)
    } else {
        memberRepository.existsByProviderAndEmailIdentifierAndIdNot(provider, emailIdentifier, excludingMemberId)
    }

    override fun existsById(memberId: Long): Boolean = memberRepository.existsById(memberId)

    override fun save(member: Member): Long {
        val saved = memberRepository.save(
            member.toNewEntity(
                encryptedEmail = encryptor.encrypt(member.email.value),
                emailIdentifier = identifierProtector.protect(member.email.value),
            ),
        )
        val memberId = requireNotNull(saved.id) { "회원 저장 후 id가 없습니다." }

        profileRepository.save(member.toNewProfileEntity(memberId))
        member.agreements.forEach { agreementRepository.save(it.toNewEntity(memberId)) }
        return memberId
    }

    override fun markEmailVerified(memberId: Long, verifiedAt: Instant) {
        val entity = memberRepository.findById(memberId).orElse(null) ?: return
        entity.emailVerified = true
        // 트랜잭션 커밋 시 dirty-check로 UPDATE 발행(@PreUpdate가 updated_at 갱신).
    }

    override fun updatePassword(memberId: Long, newPasswordHash: String) {
        val entity = memberRepository.findById(memberId).orElse(null) ?: return
        entity.passwordHash = newPasswordHash
        // 트랜잭션 커밋 시 dirty-check로 UPDATE.
    }

    override fun updateProviderRefreshToken(memberId: Long, refreshToken: String) {
        val entity = memberRepository.findById(memberId).orElse(null) ?: return
        entity.providerRefreshTokenEncrypted = encryptor.encrypt(refreshToken)
        // 트랜잭션 커밋 시 dirty-check로 UPDATE.
    }

    override fun findProviderRefreshToken(memberId: Long): String? {
        val entity = memberRepository.findById(memberId).orElse(null) ?: return null
        return entity.providerRefreshTokenEncrypted?.let { encryptor.decrypt(it) }
    }

    override fun findEmailByMemberId(memberId: Long): Email? {
        val entity = memberRepository.findById(memberId).orElse(null) ?: return null
        return Email(encryptor.decrypt(entity.emailEncrypted))
    }

    /**
     * 이메일 교체(SPEC-0011 R7·R8). managed 엔티티를 고쳐 dirty-check에 맡긴다.
     * - [protectedIdentifier]가 있으면 provider_user_id도 같은 트랜잭션에서 바꾼다. 갈리면 계정이 잠긴다.
     * - 경합하는 두 확정 중 하나는 `(provider, provider_user_id)` 유니크 제약에서 걸린다.
     */
    @Transactional
    override fun updateEmail(memberId: Long, newEmail: Email, emailIdentifier: String, protectedIdentifier: String?) {
        val entity = memberRepository.findById(memberId).orElse(null)
            ?: throw MemberNotFoundException("회원을 찾을 수 없습니다.")
        entity.emailEncrypted = encryptor.encrypt(newEmail.value)
        // 유니크 판정용 파생 값이라 이메일과 반드시 함께 바뀐다(SPEC-0024 R5). provider를 가리지 않는다.
        entity.emailIdentifier = emailIdentifier
        if (protectedIdentifier != null) {
            entity.providerUserId = protectedIdentifier
        }
    }

    override fun findAuthentication(provider: Provider, providerUserId: ProviderUserId): MemberAuthentication? {
        val entity = memberRepository.findByProviderAndProviderUserId(provider, providerUserId.value) ?: return null
        return MemberAuthentication(
            memberId = requireNotNull(entity.id),
            status = entity.status,
            suspendedUntil = entity.suspendedUntil,
            suspensionReason = entity.suspensionReason,
        )
    }

    override fun findEmailAuthentication(protectedIdentifier: String): EmailAuthentication? {
        val entity = memberRepository.findByProviderAndProviderUserId(Provider.EMAIL, protectedIdentifier) ?: return null
        return EmailAuthentication(
            memberId = requireNotNull(entity.id),
            status = entity.status,
            suspendedUntil = entity.suspendedUntil,
            suspensionReason = entity.suspensionReason,
            passwordHash = entity.passwordHash,
            emailVerified = entity.emailVerified,
        )
    }

    override fun load(memberId: Long): Member? {
        val entity = memberRepository.findById(memberId).orElse(null) ?: return null
        val profile = profileRepository.findByMemberId(memberId)
        return entity.toDomain(profile, encryptor.decrypt(entity.emailEncrypted))
    }

    override fun updateStatus(member: Member) {
        val id = member.id ?: return
        val entity = memberRepository.findById(id).orElse(null) ?: return
        entity.status = member.status
        entity.suspendedAt = member.suspendedAt
        entity.suspendedUntil = member.suspendedUntil
        entity.suspensionReason = member.suspensionReason
        // dirty-check로 UPDATE.
    }

    /**
     * 탈퇴: 회원과 소유 데이터를 지운다. 감사 로그는 FK가 없어 보존된다.
     * - `member`를 참조하는 FK 자식을 하나라도 빠뜨리면 그 행을 가진 회원의 탈퇴가 영구히 막힌다.
     *   새 테이블이 `member_id`를 참조하면 이 목록에도 추가해야 한다(MemberDeletionCoverageTest가 대조한다).
     */
    override fun deleteMember(memberId: Long) {
        emailVerificationRepository.deleteByMemberId(memberId)
        passwordResetRepository.deleteByMemberId(memberId)
        agreementRepository.deleteByMemberId(memberId)
        profileRepository.deleteByMemberId(memberId)
        memberRoleRepository.deleteByMemberId(memberId)
        memberRepository.deleteById(memberId)
    }
}
