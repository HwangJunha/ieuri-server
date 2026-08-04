package org.around.ieuri.auth.infrastructure.mapper

import org.around.ieuri.auth.domain.aggregate.Member
import org.around.ieuri.auth.domain.vo.Agreement
import org.around.ieuri.auth.domain.vo.Email
import org.around.ieuri.auth.domain.vo.MemberStatus
import org.around.ieuri.auth.domain.vo.Nickname
import org.around.ieuri.auth.domain.vo.Provider
import org.around.ieuri.auth.domain.vo.ProviderUserId
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.MemberEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.MemberProfileEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.mapper.DEFAULT_NICKNAME
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.mapper.toDomain
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.mapper.toNewEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.mapper.toNewProfileEntity
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** MemberMapper 필드별 매핑 검증 — 순수 단위테스트(Spring/DB 없음). */
class MemberMapperTest {

    private val createdAt: Instant = Instant.parse("2026-01-02T03:04:05Z")
    private val suspendedAt: Instant = Instant.parse("2026-02-03T04:05:06Z")
    private val suspendedUntil: Instant = Instant.parse("2026-03-04T05:06:07Z")
    private val agreedAt: Instant = Instant.parse("2026-04-05T06:07:08Z")

    private fun memberEntity(
        suspendedAt: Instant? = this.suspendedAt,
        suspendedUntil: Instant? = this.suspendedUntil,
        suspensionReason: String? = "약관 위반",
        passwordHash: String? = "hash-1",
    ) = MemberEntity(
        id = 7L,
        provider = Provider.KAKAO,
        providerUserId = "kakao-9911",
        emailEncrypted = "enc-blob",
        emailVerified = true,
        passwordHash = passwordHash,
        status = MemberStatus.SUSPENDED,
        ageVerified = true,
        suspendedAt = suspendedAt,
        suspendedUntil = suspendedUntil,
        suspensionReason = suspensionReason,
        createdAt = createdAt,
        updatedAt = Instant.parse("2026-05-06T07:08:09Z"),
    )

    private fun profileEntity() = MemberProfileEntity(
        id = 11L,
        memberId = 7L,
        nickname = "여행하는사람",
        profileImageUrl = "https://cdn.example.com/p/7.png",
    )

    private fun member(
        profileImageUrl: String? = "https://cdn.example.com/p/7.png",
    ): Member = Member.reconstitute(
        id = 7L,
        provider = Provider.KAKAO,
        providerUserId = ProviderUserId("kakao-9911"),
        email = Email("member@example.com"),
        emailVerified = true,
        status = MemberStatus.SUSPENDED,
        ageVerified = true,
        suspendedAt = suspendedAt,
        suspendedUntil = suspendedUntil,
        suspensionReason = "약관 위반",
        nickname = Nickname("여행하는사람"),
        profileImageUrl = profileImageUrl,
        createdAt = createdAt,
    )

    // ── toDomain ────────────────────────────────────────────────────

    @Test
    fun `모든 필드가 채워진 회원 엔티티를 도메인으로 옮긴다`() {
        val domain = memberEntity().toDomain(profileEntity(), "member@example.com")

        assertEquals(7L, domain.id)
        assertEquals(Provider.KAKAO, domain.provider)
        assertEquals("kakao-9911", domain.providerUserId.value)
        assertEquals("member@example.com", domain.email.value)
        assertEquals(true, domain.emailVerified)
        assertEquals(MemberStatus.SUSPENDED, domain.status)
        assertEquals(true, domain.ageVerified)
        assertEquals(suspendedAt, domain.suspendedAt)
        assertEquals(suspendedUntil, domain.suspendedUntil)
        assertEquals("약관 위반", domain.suspensionReason)
        assertEquals("여행하는사람", domain.profile.nickname.value)
        assertEquals("https://cdn.example.com/p/7.png", domain.profile.profileImageUrl)
        assertEquals(createdAt, domain.createdAt)
    }

    @Test
    fun `nullable 필드가 모두 null이어도 예외 없이 옮긴다`() {
        val domain = memberEntity(
            suspendedAt = null,
            suspendedUntil = null,
            suspensionReason = null,
            passwordHash = null,
        ).toDomain(profile = null, decryptedEmail = "member@example.com")

        assertNull(domain.suspendedAt)
        assertNull(domain.suspendedUntil)
        assertNull(domain.suspensionReason)
        assertNull(domain.profile.profileImageUrl)
        // 프로필 행이 없으면 기본 닉네임으로 채운다.
        assertEquals(DEFAULT_NICKNAME, domain.profile.nickname.value)
    }

    @Test
    fun `이메일은 복호화된 인자를 쓰고 엔티티의 암호문은 쓰지 않는다`() {
        val domain = memberEntity().toDomain(profileEntity(), "decrypted@example.com")

        assertEquals("decrypted@example.com", domain.email.value)
    }

    // ── toNewEntity ─────────────────────────────────────────────────

    @Test
    fun `도메인 회원을 새 엔티티로 옮긴다`() {
        val entity = member().toNewEntity("enc-blob")

        assertNull(entity.id) // id는 DB가 채운다.
        assertEquals(Provider.KAKAO, entity.provider)
        assertEquals("kakao-9911", entity.providerUserId)
        assertEquals("enc-blob", entity.emailEncrypted)
        assertEquals(true, entity.emailVerified)
        assertEquals(MemberStatus.SUSPENDED, entity.status)
        assertEquals(true, entity.ageVerified)
        // reconstitute는 passwordHash를 싣지 않으므로 SNS 회원과 같은 null이다.
        assertNull(entity.passwordHash)
    }

    // ── toNewProfileEntity ──────────────────────────────────────────

    @Test
    fun `도메인 프로필을 새 프로필 엔티티로 옮긴다`() {
        val entity = member().toNewProfileEntity(memberId = 42L)

        assertNull(entity.id)
        assertEquals(42L, entity.memberId)
        assertEquals("여행하는사람", entity.nickname)
        assertEquals("https://cdn.example.com/p/7.png", entity.profileImageUrl)
    }

    @Test
    fun `프로필 이미지가 없으면 null 그대로 옮긴다`() {
        val entity = member(profileImageUrl = null).toNewProfileEntity(memberId = 42L)

        assertEquals("여행하는사람", entity.nickname)
        assertNull(entity.profileImageUrl)
    }

    // ── Agreement.toNewEntity ───────────────────────────────────────

    @Test
    fun `동의 이력을 새 엔티티로 옮긴다`() {
        val entity = Agreement(
            termsId = 3L,
            termsCode = "PRIVACY",
            termsVersion = "2.1",
            agreed = true,
            agreedAt = agreedAt,
        ).toNewEntity(memberId = 55L)

        assertNull(entity.id)
        assertEquals(55L, entity.memberId)
        assertEquals(3L, entity.termsId)
        assertEquals("PRIVACY", entity.termsCode)
        assertEquals("2.1", entity.termsVersion)
        assertEquals(true, entity.agreed)
        assertEquals(agreedAt, entity.agreedAt)
    }

    @Test
    fun `비동의 이력도 값 그대로 옮긴다`() {
        val entity = Agreement(
            termsId = 4L,
            termsCode = "MARKETING",
            termsVersion = "1.0",
            agreed = false,
            agreedAt = agreedAt,
        ).toNewEntity(memberId = 55L)

        assertEquals("MARKETING", entity.termsCode)
        assertEquals(false, entity.agreed)
    }
}
