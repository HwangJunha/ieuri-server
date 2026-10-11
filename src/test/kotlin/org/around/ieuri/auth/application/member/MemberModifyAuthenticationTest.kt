package org.around.ieuri.auth.application.member

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.auth.application.member.provided.dto.EmailCredentialView
import org.around.ieuri.auth.application.member.provided.exception.MemberNotFoundException
import org.around.ieuri.auth.application.member.required.MemberRepository
import org.around.ieuri.auth.domain.member.InactiveMemberException
import org.around.ieuri.auth.domain.member.Member
import org.around.ieuri.auth.domain.member.MemberStatus
import org.around.ieuri.auth.domain.member.Provider
import org.around.ieuri.auth.domain.member.ProviderRefreshToken
import org.around.ieuri.auth.domain.member.ProviderUserId
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** 로그인의 DB 단계. 정지 판정은 도메인이 하고, 서비스는 기간제 정지가 만료돼 복구됐을 때만 상태를 쓴다. */
class MemberModifyAuthenticationTest {

    private val now = Instant.parse("2026-07-17T00:00:00Z")

    private val memberRepository = mockk<MemberRepository>(relaxUnitFun = true)
    private val service = memberModifyService(memberRepository, clock = Clock.fixed(now, ZoneOffset.UTC))

    private fun snsMember(status: MemberStatus = MemberStatus.ACTIVE, until: Instant? = null, reason: String? = null) = memberFixture(
        provider = Provider.GOOGLE,
        providerUserId = "google-sub-1",
        status = status,
        suspendedAt = if (status == MemberStatus.SUSPENDED) now.minusSeconds(100) else null,
        suspendedUntil = until,
        suspensionReason = reason,
    )

    private fun givenSnsMember(member: Member?) {
        every { memberRepository.findByProviderUserId(Provider.GOOGLE, ProviderUserId("google-sub-1")) } returns member
    }

    // --- SNS 로그인 ---

    @Test
    fun `활성 회원은 상태를 쓰지 않고 회원 id를 돌려준다`() {
        givenSnsMember(snsMember())

        assertEquals(100L, service.admitSns(Provider.GOOGLE, "google-sub-1"))

        verify(exactly = 0) { memberRepository.saveStatus(any()) }
    }

    @Test
    fun `미가입 신원은 회원가입을 안내하는 메시지로 거부한다`() {
        givenSnsMember(null)

        val ex = assertFailsWith<MemberNotFoundException> { service.admitSns(Provider.GOOGLE, "google-sub-1") }

        assertEquals("가입되지 않은 계정입니다. 회원가입이 필요합니다.", ex.message)
    }

    @Test
    fun `무기한 정지 회원은 사유를 붙여 거부하고 상태를 쓰지 않는다`() {
        givenSnsMember(snsMember(status = MemberStatus.SUSPENDED, until = null, reason = "약관 위반"))

        val ex = assertFailsWith<InactiveMemberException> { service.admitSns(Provider.GOOGLE, "google-sub-1") }

        assertEquals("ACCOUNT_INACTIVE", ex.code)
        assertEquals("정지된 계정입니다. 사유: 약관 위반", ex.message)
        verify(exactly = 0) { memberRepository.saveStatus(any()) }
    }

    @Test
    fun `만료일이 아직 지나지 않은 정지 회원은 거부한다`() {
        givenSnsMember(snsMember(status = MemberStatus.SUSPENDED, until = now.plusSeconds(1), reason = "약관 위반"))

        assertFailsWith<InactiveMemberException> { service.admitSns(Provider.GOOGLE, "google-sub-1") }

        verify(exactly = 0) { memberRepository.saveStatus(any()) }
    }

    /** 만료 시각에 도달한 순간부터 복구된다. */
    @Test
    fun `만료일에 도달한 기간제 정지 회원은 복구된 상태를 저장하고 로그인시킨다`() {
        givenSnsMember(snsMember(status = MemberStatus.SUSPENDED, until = now, reason = "약관 위반"))

        assertEquals(100L, service.admitSns(Provider.GOOGLE, "google-sub-1"))

        verify(exactly = 1) { memberRepository.saveStatus(snsMember(status = MemberStatus.ACTIVE)) }
    }

    // --- 이메일 로그인 ---

    @Test
    fun `이메일 자격은 EMAIL provider의 식별자로 찾아 회원 id와 해시를 돌려준다`() {
        every { memberRepository.findByProviderUserId(Provider.EMAIL, ProviderUserId("protected:user@example.com")) } returns
            memberFixture(id = 300L, provider = Provider.EMAIL, providerUserId = "protected:user@example.com", passwordHash = "hashed-pw")

        assertEquals(EmailCredentialView(memberId = 300L, passwordHash = "hashed-pw"), service.findEmailCredential("protected:user@example.com"))
    }

    @Test
    fun `없는 계정의 이메일 자격은 null이고 비밀번호가 없는 계정은 해시가 null이다`() {
        every { memberRepository.findByProviderUserId(Provider.EMAIL, ProviderUserId("ghost")) } returns null
        every { memberRepository.findByProviderUserId(Provider.EMAIL, ProviderUserId("no-password")) } returns
            memberFixture(id = 300L, provider = Provider.EMAIL, providerUserId = "no-password", passwordHash = null)

        assertNull(service.findEmailCredential("ghost"))
        assertEquals(EmailCredentialView(memberId = 300L, passwordHash = null), service.findEmailCredential("no-password"))
    }

    @Test
    fun `비밀번호 대조를 마친 회원도 정지 판정은 같다`() {
        every { memberRepository.findById(1L) } returns memberFixture(id = 1L)
        every { memberRepository.findById(2L) } returns memberFixture(id = 2L, status = MemberStatus.SUSPENDED, suspendedAt = now.minusSeconds(10))
        every { memberRepository.findById(3L) } returns
            memberFixture(id = 3L, status = MemberStatus.SUSPENDED, suspendedAt = now.minusSeconds(10), suspendedUntil = now.minusSeconds(1), suspensionReason = "사유")
        every { memberRepository.findById(4L) } returns null

        service.admit(1L)
        val suspended = assertFailsWith<InactiveMemberException> { service.admit(2L) }
        service.admit(3L)
        val missing = assertFailsWith<MemberNotFoundException> { service.admit(4L) }

        // 사유가 없으면 붙이지 않는다.
        assertEquals("정지된 계정입니다.", suspended.message)
        assertEquals("회원을 찾을 수 없습니다.", missing.message)
        verify(exactly = 1) { memberRepository.saveStatus(any()) }
        verify(exactly = 1) { memberRepository.saveStatus(memberFixture(id = 3L, status = MemberStatus.ACTIVE)) }
    }

    // --- provider 토큰 보관 ---

    @Test
    fun `SNS 회원의 provider 토큰은 회원 id와 함께 저장한다`() {
        every { memberRepository.findById(100L) } returns snsMember()

        service.keepProviderRefreshToken(100L, "provider-refresh-1")

        verify(exactly = 1) { memberRepository.saveProviderRefreshToken(ProviderRefreshToken(100L, "provider-refresh-1")) }
    }

    /** 자체(EMAIL) 회원은 대상이 아니고(SPEC-0012 R5), 그 사이 탈퇴한 회원도 조용히 넘어간다. */
    @Test
    fun `자체 회원과 없는 회원의 provider 토큰은 저장하지 않는다`() {
        every { memberRepository.findById(100L) } returns memberFixture(provider = Provider.EMAIL)
        every { memberRepository.findById(404L) } returns null

        service.keepProviderRefreshToken(100L, "provider-refresh-1")
        service.keepProviderRefreshToken(404L, "provider-refresh-1")

        verify(exactly = 0) { memberRepository.saveProviderRefreshToken(any()) }
    }
}
