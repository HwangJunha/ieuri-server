package org.around.ieuri.auth.application

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.auth.application.command.LoginCommand
import org.around.ieuri.auth.application.exception.InactiveMemberException
import org.around.ieuri.auth.application.exception.MemberNotFoundException
import org.around.ieuri.auth.application.port.`in`.command.TokenPairResult
import org.around.ieuri.auth.application.port.out.client.SnsIdentity
import org.around.ieuri.auth.application.port.out.client.SnsIdentityVerifierPort
import org.around.ieuri.auth.application.port.out.persistence.MemberAuthentication
import org.around.ieuri.auth.application.port.out.persistence.MemberRepositoryPort
import org.around.ieuri.auth.domain.aggregate.Member
import org.around.ieuri.auth.domain.vo.Email
import org.around.ieuri.auth.domain.vo.MemberStatus
import org.around.ieuri.auth.domain.vo.Nickname
import org.around.ieuri.auth.domain.vo.Provider
import org.around.ieuri.auth.domain.vo.ProviderUserId
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** LoginHandler — SNS 인증 후 회원 조회·상태 확인·토큰 발급, 정지 만료 자동 복구 검증. */
class LoginHandlerTest {

    private val snsVerifier = mockk<SnsIdentityVerifierPort>()
    private val memberRepository = mockk<MemberRepositoryPort>(relaxed = true)
    private val tokenIssuer = mockk<TokenIssuer>()
    private val now = Instant.parse("2026-07-17T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val handler = LoginHandler(snsVerifier, memberRepository, tokenIssuer, clock)

    private val tokens = TokenPairResult("acc", "ref", "Bearer", 300, 2_592_000)
    private val command = LoginCommand(Provider.GOOGLE, "dev-token")

    private fun identity() = SnsIdentity("google-sub-1", "u@example.com", true, "여행자", null)

    private fun suspendedMember(until: Instant?) = Member.reconstitute(
        id = 100L, provider = Provider.GOOGLE, providerUserId = ProviderUserId("google-sub-1"),
        email = Email("u@example.com"), emailVerified = true, status = MemberStatus.SUSPENDED, ageVerified = true,
        suspendedAt = now.minusSeconds(100), suspendedUntil = until, suspensionReason = "테스트",
        nickname = Nickname("여행자"), profileImageUrl = null, createdAt = now.minusSeconds(1000),
    )

    @Test
    fun `ACTIVE 회원이면 토큰을 발급한다`() {
        every { snsVerifier.verify(any(), any()) } returns identity()
        every { memberRepository.findAuthentication(any(), any()) } returns MemberAuthentication(100L, MemberStatus.ACTIVE, null, null)
        every { tokenIssuer.issueFor(100L) } returns tokens

        assertEquals(tokens, handler.handle(command))
        verify { tokenIssuer.issueFor(100L) }
    }

    @Test
    fun `미가입 회원이면 MemberNotFoundException`() {
        every { snsVerifier.verify(any(), any()) } returns identity()
        every { memberRepository.findAuthentication(any(), any()) } returns null

        assertFailsWith<MemberNotFoundException> { handler.handle(command) }
    }

    @Test
    fun `무기한 정지(만료일 없음)면 InactiveMemberException`() {
        every { snsVerifier.verify(any(), any()) } returns identity()
        every { memberRepository.findAuthentication(any(), any()) } returns
            MemberAuthentication(100L, MemberStatus.SUSPENDED, null, "테스트")

        assertFailsWith<InactiveMemberException> { handler.handle(command) }
        verify(exactly = 0) { tokenIssuer.issueFor(any()) }
    }

    @Test
    fun `만료일이 아직 안 지난 정지면 InactiveMemberException`() {
        every { snsVerifier.verify(any(), any()) } returns identity()
        every { memberRepository.findAuthentication(any(), any()) } returns
            MemberAuthentication(100L, MemberStatus.SUSPENDED, now.plusSeconds(3600), "테스트")

        assertFailsWith<InactiveMemberException> { handler.handle(command) }
        verify(exactly = 0) { tokenIssuer.issueFor(any()) }
    }

    @Test
    fun `만료일이 지난 기간제 정지면 자동 복구 후 토큰을 발급한다`() {
        every { snsVerifier.verify(any(), any()) } returns identity()
        every { memberRepository.findAuthentication(any(), any()) } returns
            MemberAuthentication(100L, MemberStatus.SUSPENDED, now.minusSeconds(1), "테스트")
        every { memberRepository.load(100L) } returns suspendedMember(until = now.minusSeconds(1))
        every { tokenIssuer.issueFor(100L) } returns tokens

        assertEquals(tokens, handler.handle(command))
        verify { memberRepository.updateStatus(any()) } // 자동 복구가 반영됨
        verify { tokenIssuer.issueFor(100L) }
    }
}
