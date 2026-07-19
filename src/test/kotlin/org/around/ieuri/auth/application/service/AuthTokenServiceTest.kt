package org.around.ieuri.auth.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.auth.application.command.LoginCommand
import org.around.ieuri.auth.application.command.LoginEmailCommand
import org.around.ieuri.auth.application.command.LogoutCommand
import org.around.ieuri.auth.application.command.RefreshTokenCommand
import org.around.ieuri.auth.application.exception.EmailNotVerifiedException
import org.around.ieuri.auth.application.exception.InactiveMemberException
import org.around.ieuri.auth.application.exception.InvalidCredentialsException
import org.around.ieuri.auth.application.exception.InvalidTokenException
import org.around.ieuri.auth.application.exception.MemberNotFoundException
import org.around.ieuri.auth.application.exception.TokenReuseDetectedException
import org.around.ieuri.auth.application.port.`in`.command.TokenPairResult
import org.around.ieuri.auth.application.port.out.client.SnsIdentity
import org.around.ieuri.auth.application.port.out.client.SnsIdentityVerifierPort
import org.around.ieuri.auth.application.port.out.persistence.EmailAuthentication
import org.around.ieuri.auth.application.port.out.persistence.MemberAuthentication
import org.around.ieuri.auth.application.port.out.persistence.MemberRepositoryPort
import org.around.ieuri.auth.application.port.out.security.PasswordEncoderPort
import org.around.ieuri.auth.application.port.out.token.RefreshTokenClaims
import org.around.ieuri.auth.application.port.out.token.RefreshTokenStorePort
import org.around.ieuri.auth.application.port.out.token.TokenProviderPort
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
import kotlin.test.assertTrue

/** AuthTokenService — SNS 로그인·refresh 회전·로그아웃 오케스트레이션 검증. */
class AuthTokenServiceTest {

    private val snsVerifier = mockk<SnsIdentityVerifierPort>()
    private val memberRepository = mockk<MemberRepositoryPort>(relaxed = true)
    private val tokenProvider = mockk<TokenProviderPort>()
    private val refreshTokenStore = mockk<RefreshTokenStorePort>(relaxed = true)
    private val tokenIssuer = mockk<TokenIssuer>()
    private val passwordEncoder = mockk<PasswordEncoderPort>()
    private val now = Instant.parse("2026-07-17T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private val service = AuthTokenService(
        snsVerifier, memberRepository, tokenProvider, refreshTokenStore, tokenIssuer, passwordEncoder, clock,
    )

    private val tokens = TokenPairResult("acc", "ref", "Bearer", 300, 2_592_000)

    private fun identity() = SnsIdentity("google-sub-1", "u@example.com", true, "여행자", null)

    private fun suspendedMember(until: Instant?) = Member.reconstitute(
        id = 100L, provider = Provider.GOOGLE, providerUserId = ProviderUserId("google-sub-1"),
        email = Email("u@example.com"), emailVerified = true, status = MemberStatus.SUSPENDED, ageVerified = true,
        suspendedAt = now.minusSeconds(100), suspendedUntil = until, suspensionReason = "테스트",
        nickname = Nickname("여행자"), profileImageUrl = null, createdAt = now.minusSeconds(1000),
    )

    // --- loginBySns ---

    private val loginCommand = LoginCommand(Provider.GOOGLE, "dev-token")

    @Test
    fun `ACTIVE 회원이면 토큰을 발급한다`() {
        every { snsVerifier.verify(any(), any()) } returns identity()
        every { memberRepository.findAuthentication(any(), any()) } returns MemberAuthentication(100L, MemberStatus.ACTIVE, null, null)
        every { tokenIssuer.issueFor(100L) } returns tokens

        assertEquals(tokens, service.loginBySns(loginCommand))
        verify { tokenIssuer.issueFor(100L) }
    }

    @Test
    fun `미가입 회원이면 MemberNotFoundException`() {
        every { snsVerifier.verify(any(), any()) } returns identity()
        every { memberRepository.findAuthentication(any(), any()) } returns null

        assertFailsWith<MemberNotFoundException> { service.loginBySns(loginCommand) }
    }

    @Test
    fun `무기한 정지(만료일 없음)면 InactiveMemberException`() {
        every { snsVerifier.verify(any(), any()) } returns identity()
        every { memberRepository.findAuthentication(any(), any()) } returns
            MemberAuthentication(100L, MemberStatus.SUSPENDED, null, "테스트")

        assertFailsWith<InactiveMemberException> { service.loginBySns(loginCommand) }
        verify(exactly = 0) { tokenIssuer.issueFor(any()) }
    }

    @Test
    fun `만료일이 아직 안 지난 정지면 InactiveMemberException`() {
        every { snsVerifier.verify(any(), any()) } returns identity()
        every { memberRepository.findAuthentication(any(), any()) } returns
            MemberAuthentication(100L, MemberStatus.SUSPENDED, now.plusSeconds(3600), "테스트")

        assertFailsWith<InactiveMemberException> { service.loginBySns(loginCommand) }
        verify(exactly = 0) { tokenIssuer.issueFor(any()) }
    }

    @Test
    fun `만료일이 지난 기간제 정지면 자동 복구 후 토큰을 발급한다`() {
        every { snsVerifier.verify(any(), any()) } returns identity()
        every { memberRepository.findAuthentication(any(), any()) } returns
            MemberAuthentication(100L, MemberStatus.SUSPENDED, now.minusSeconds(1), "테스트")
        every { memberRepository.load(100L) } returns suspendedMember(until = now.minusSeconds(1))
        every { tokenIssuer.issueFor(100L) } returns tokens

        assertEquals(tokens, service.loginBySns(loginCommand))
        verify { memberRepository.updateStatus(any()) } // 자동 복구가 반영됨
        verify { tokenIssuer.issueFor(100L) }
    }

    // --- loginByEmail ---

    private val emailLoginCommand = LoginEmailCommand("user@example.com", "Passw0rd!")

    private fun emailAuth(
        passwordHash: String? = "hashed-pw",
        emailVerified: Boolean = true,
        status: MemberStatus = MemberStatus.ACTIVE,
    ) = EmailAuthentication(100L, status, null, null, passwordHash, emailVerified)

    @Test
    fun `이메일-비밀번호가 맞고 인증완료면 토큰을 발급한다`() {
        every { memberRepository.findEmailAuthentication("user@example.com") } returns emailAuth()
        every { passwordEncoder.matches("Passw0rd!", "hashed-pw") } returns true
        every { tokenIssuer.issueFor(100L) } returns tokens

        assertEquals(tokens, service.loginByEmail(emailLoginCommand))
        verify { tokenIssuer.issueFor(100L) }
    }

    @Test
    fun `없는 이메일이면 InvalidCredentialsException`() {
        every { memberRepository.findEmailAuthentication(any()) } returns null

        assertFailsWith<InvalidCredentialsException> { service.loginByEmail(emailLoginCommand) }
        verify(exactly = 0) { tokenIssuer.issueFor(any()) }
    }

    @Test
    fun `비밀번호가 틀리면 InvalidCredentialsException`() {
        every { memberRepository.findEmailAuthentication(any()) } returns emailAuth()
        every { passwordEncoder.matches(any(), any()) } returns false

        assertFailsWith<InvalidCredentialsException> { service.loginByEmail(emailLoginCommand) }
        verify(exactly = 0) { tokenIssuer.issueFor(any()) }
    }

    @Test
    fun `SNS 계정(비밀번호 없음)으로 이메일 로그인하면 InvalidCredentialsException`() {
        every { memberRepository.findEmailAuthentication(any()) } returns emailAuth(passwordHash = null)

        assertFailsWith<InvalidCredentialsException> { service.loginByEmail(emailLoginCommand) }
    }

    @Test
    fun `이메일 미인증 계정이면 EmailNotVerifiedException`() {
        every { memberRepository.findEmailAuthentication(any()) } returns emailAuth(emailVerified = false)
        every { passwordEncoder.matches(any(), any()) } returns true

        assertFailsWith<EmailNotVerifiedException> { service.loginByEmail(emailLoginCommand) }
        verify(exactly = 0) { tokenIssuer.issueFor(any()) }
    }

    // --- refresh ---

    private val claims = RefreshTokenClaims(memberId = 100L, jti = "jti-1", expiresAt = Instant.parse("2026-08-16T00:00:00Z"))

    @Test
    fun `저장소에 살아있는 refresh면 회전(이전 폐기 + 재발급)한다`() {
        every { tokenProvider.parseRefreshToken("ref") } returns claims
        every { refreshTokenStore.existsActive("jti-1") } returns true
        every { tokenIssuer.issueFor(100L) } returns tokens

        val result = service.refresh(RefreshTokenCommand("ref"))

        assertEquals(tokens, result)
        verify { refreshTokenStore.revoke("jti-1") }
        verify { tokenIssuer.issueFor(100L) }
    }

    @Test
    fun `이미 폐기된 refresh 재사용이면 회원 전체 폐기 후 TokenReuseDetectedException`() {
        every { tokenProvider.parseRefreshToken("ref") } returns claims
        every { refreshTokenStore.existsActive("jti-1") } returns false

        assertFailsWith<TokenReuseDetectedException> { service.refresh(RefreshTokenCommand("ref")) }
        verify { refreshTokenStore.revokeAllByMember(100L) }
        verify(exactly = 0) { tokenIssuer.issueFor(any()) }
    }

    @Test
    fun `유효하지 않은 토큰이면 InvalidTokenException이 전파된다`() {
        every { tokenProvider.parseRefreshToken("bad") } throws InvalidTokenException("invalid")

        assertFailsWith<InvalidTokenException> { service.refresh(RefreshTokenCommand("bad")) }
        verify(exactly = 0) { tokenIssuer.issueFor(any()) }
    }

    // --- logout ---

    @Test
    fun `유효한 refresh면 해당 jti를 폐기한다`() {
        every { tokenProvider.parseRefreshToken("ref") } returns
            RefreshTokenClaims(100L, "jti-1", Instant.parse("2026-08-16T00:00:00Z"))

        val result = service.logout(LogoutCommand("ref"))

        assertTrue(result.success)
        verify { refreshTokenStore.revoke("jti-1") }
    }

    @Test
    fun `무효한 토큰이어도 멱등하게 성공 처리하고 폐기하지 않는다`() {
        every { tokenProvider.parseRefreshToken("bad") } throws InvalidTokenException("invalid")

        val result = service.logout(LogoutCommand("bad"))

        assertTrue(result.success)
        verify(exactly = 0) { refreshTokenStore.revoke(any()) }
    }
}
