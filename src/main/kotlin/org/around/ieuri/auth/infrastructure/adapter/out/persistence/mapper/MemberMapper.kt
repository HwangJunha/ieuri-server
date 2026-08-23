package org.around.ieuri.auth.infrastructure.adapter.out.persistence.mapper

import org.around.ieuri.auth.domain.aggregate.Member
import org.around.ieuri.auth.domain.vo.Email
import org.around.ieuri.auth.domain.vo.Nickname
import org.around.ieuri.auth.domain.vo.Agreement
import org.around.ieuri.auth.domain.vo.ProviderUserId
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.MemberEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.MemberProfileEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.MemberTermsAgreementEntity

/** 프로필 행이 없을 때 쓰는 기본 닉네임. */
const val DEFAULT_NICKNAME = "여행자"

/**
 * 회원 엔티티와 프로필 행을 도메인 [Member]로 옮긴다.
 *
 * [decryptedEmail]을 인자로 받는다 — 복호화는 암호화 포트의 책임이므로 여기서 하지 않는다.
 */
fun MemberEntity.toDomain(profile: MemberProfileEntity?, decryptedEmail: String): Member =
    Member.reconstitute(
        id = requireNotNull(id),
        provider = provider,
        providerUserId = ProviderUserId(providerUserId),
        email = Email(decryptedEmail),
        emailVerified = emailVerified,
        status = status,
        ageVerified = ageVerified,
        suspendedAt = suspendedAt,
        suspendedUntil = suspendedUntil,
        suspensionReason = suspensionReason,
        nickname = Nickname(profile?.nickname ?: DEFAULT_NICKNAME),
        profileImageUrl = profile?.profileImageUrl,
        createdAt = requireNotNull(createdAt),
    )

/**
 * 도메인 [Member]를 새 엔티티로 옮긴다. id는 DB가 채운다.
 *
 * [encryptedEmail]을 인자로 받는다 — 암호화는 암호화 포트의 책임이므로 여기서 하지 않는다.
 */
/**
 * 신규 저장용 엔티티. 암호화와 식별자 계산은 설정이 필요한 관심사라 **호출자가 계산해 넘긴다** —
 * 매퍼는 순수 함수로 남는다(POL-033).
 *
 * [emailIdentifier]는 `(provider, email)` 유니크 판정용 파생 값이다(SPEC-0024 R5).
 */
fun Member.toNewEntity(encryptedEmail: String, emailIdentifier: String): MemberEntity =
    MemberEntity(
        provider = provider,
        providerUserId = providerUserId.value,
        emailEncrypted = encryptedEmail,
        emailIdentifier = emailIdentifier,
        emailVerified = emailVerified,
        passwordHash = passwordHash?.value,
        status = status,
        ageVerified = ageVerified,
    )

/** 도메인 [Member]의 프로필을 새 프로필 엔티티로 옮긴다. */
fun Member.toNewProfileEntity(memberId: Long): MemberProfileEntity =
    MemberProfileEntity(
        memberId = memberId,
        nickname = profile.nickname.value,
        profileImageUrl = profile.profileImageUrl,
    )

/** 도메인 [Agreement]를 새 동의 이력 엔티티로 옮긴다. */
fun Agreement.toNewEntity(memberId: Long): MemberTermsAgreementEntity =
    MemberTermsAgreementEntity(
        memberId = memberId,
        termsId = termsId,
        termsCode = termsCode,
        termsVersion = termsVersion,
        agreed = agreed,
        agreedAt = agreedAt,
    )
