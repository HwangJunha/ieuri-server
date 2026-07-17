package org.around.ieuri.auth.infrastructure.adapter.out.persistence

import org.around.ieuri.auth.application.port.out.persistence.MemberAuthentication
import org.around.ieuri.auth.application.port.out.persistence.MemberRepositoryPort
import org.around.ieuri.auth.application.port.out.security.EmailEncryptorPort
import org.around.ieuri.auth.domain.aggregate.Member
import org.around.ieuri.auth.domain.vo.Email
import org.around.ieuri.auth.domain.vo.Provider
import org.around.ieuri.auth.domain.vo.ProviderUserId
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.MemberEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.MemberProfileEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.MemberTermsAgreementEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.repository.MemberJpaRepository
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.repository.MemberProfileJpaRepository
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.repository.MemberTermsAgreementJpaRepository
import org.springframework.stereotype.Component
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
    private val encryptor: EmailEncryptorPort,
) : MemberRepositoryPort {

    override fun existsByProviderAndProviderUserId(provider: Provider, providerUserId: ProviderUserId): Boolean =
        memberRepository.existsByProviderAndProviderUserId(provider, providerUserId.value)

    override fun save(member: Member): Long {
        val saved = memberRepository.save(
            MemberEntity(
                provider = member.provider,
                providerUserId = member.providerUserId.value,
                emailEncrypted = encryptor.encrypt(member.email.value),
                emailVerified = member.emailVerified,
                status = member.status,
                ageVerified = member.ageVerified,
            ),
        )
        val memberId = requireNotNull(saved.id) { "회원 저장 후 id가 없습니다." }

        profileRepository.save(
            MemberProfileEntity(
                memberId = memberId,
                nickname = member.profile.nickname.value,
                profileImageUrl = member.profile.profileImageUrl,
            ),
        )

        member.agreements.forEach { agreement ->
            agreementRepository.save(
                MemberTermsAgreementEntity(
                    memberId = memberId,
                    termsId = agreement.termsId,
                    termsCode = agreement.termsCode,
                    termsVersion = agreement.termsVersion,
                    agreed = agreement.agreed,
                    agreedAt = agreement.agreedAt,
                ),
            )
        }
        return memberId
    }

    override fun markEmailVerified(memberId: Long, verifiedAt: Instant) {
        val entity = memberRepository.findById(memberId).orElse(null) ?: return
        entity.emailVerified = true
        // 트랜잭션 커밋 시 dirty-check로 UPDATE 발행(@PreUpdate가 updated_at 갱신).
    }

    override fun findEmailByMemberId(memberId: Long): Email? {
        val entity = memberRepository.findById(memberId).orElse(null) ?: return null
        return Email(encryptor.decrypt(entity.emailEncrypted))
    }

    override fun findAuthentication(provider: Provider, providerUserId: ProviderUserId): MemberAuthentication? {
        val entity = memberRepository.findByProviderAndProviderUserId(provider, providerUserId.value) ?: return null
        return MemberAuthentication(memberId = requireNotNull(entity.id), status = entity.status)
    }
}
