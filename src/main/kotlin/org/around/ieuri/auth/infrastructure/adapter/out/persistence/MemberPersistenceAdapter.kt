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
import org.around.ieuri.common.security.EmailVerificationStatusPort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 회원 쓰기 포트 구현. 도메인 Member를 JPA 엔티티로 매핑해 저장하고, 이메일은 암호화해 저장한다.
 * 수정(markEmailVerified)은 managed 엔티티를 로드해 필드를 바꾸고 Hibernate dirty-check에 맡긴다.
 */
@Component
class MemberPersistenceAdapter(
    private val memberRepository: MemberJpaRepository,
    private val profileRepository: MemberProfileJpaRepository,
    private val agreementRepository: MemberTermsAgreementJpaRepository,
    private val emailVerificationRepository: EmailVerificationJpaRepository,
    private val memberRoleRepository: MemberRoleJpaRepository,
    private val encryptor: EmailEncryptorPort,
    private val identifierProtector: LoginIdentifierProtectorPort,
) : MemberRepositoryPort, EmailVerificationStatusPort {

    /**
     * 미인증 게이트(SPEC-0024 R9)가 매 요청 부른다. 없는 회원은 거짓 —
     * 존재하지 않는 주체를 통과시키지 않는다.
     */
    override fun isEmailVerified(memberId: Long): Boolean =
        memberRepository.findById(memberId).orElse(null)?.emailVerified ?: false

    override fun existsByProviderAndProviderUserId(provider: Provider, providerUserId: ProviderUserId): Boolean =
        memberRepository.existsByProviderAndProviderUserId(provider, providerUserId.value)

    override fun existsByProviderAndEmailIdentifier(
        provider: Provider,
        emailIdentifier: String,
        excludingMemberId: Long?,
    ): Boolean =
        if (excludingMemberId == null) {
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
     *
     * **[protectedIdentifier]가 있으면 provider_user_id도 같은 트랜잭션에서 바꾼다** —
     * `Provider.EMAIL` 회원은 그 컬럼이 로그인 식별자라, 이메일만 바꾸면 다음 로그인이
     * 실패한다. 둘이 갈리면 계정이 잠기므로 한 메서드로 묶었다.
     *
     * `(provider, provider_user_id)` 유니크 제약이 최종 판정자다 — 경합으로 같은 주소를
     * 동시에 확정하려는 두 요청 중 하나는 여기서 걸린다.
     */
    @Transactional
    override fun updateEmail(memberId: Long, newEmail: Email, emailIdentifier: String, protectedIdentifier: String?) {
        val entity = memberRepository.findById(memberId).orElse(null)
            ?: throw MemberNotFoundException("회원을 찾을 수 없습니다.")
        entity.emailEncrypted = encryptor.encrypt(newEmail.value)
        // 유니크 판정용 파생 값이라 이메일과 **반드시 함께** 바뀐다(SPEC-0024 R5) —
        // 어긋나면 제약이 엉뚱한 주소를 막는다. provider를 가리지 않는다.
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

    override fun deleteMember(memberId: Long) {
        // FK 자식부터 삭제 후 회원 삭제(감사 로그는 FK가 없어 보존됨).
        emailVerificationRepository.deleteByMemberId(memberId)
        agreementRepository.deleteByMemberId(memberId)
        profileRepository.deleteByMemberId(memberId)
        memberRoleRepository.deleteByMemberId(memberId)
        memberRepository.deleteById(memberId)
    }
}
