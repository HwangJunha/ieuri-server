package org.around.ieuri.auth.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.auth.application.command.LoginCommand
import org.around.ieuri.auth.application.command.LoginEmailCommand
import org.around.ieuri.auth.application.command.LogoutCommand
import org.around.ieuri.auth.application.command.RefreshTokenCommand
import org.around.ieuri.auth.application.exception.InactiveMemberException
import org.around.ieuri.auth.application.exception.InvalidCredentialsException
import org.around.ieuri.auth.application.exception.InvalidTokenException
import org.around.ieuri.auth.application.exception.MemberNotFoundException
import org.around.ieuri.auth.application.exception.TokenReuseDetectedException
import org.around.ieuri.auth.application.port.`in`.command.TokenPairResult
import org.around.ieuri.auth.application.port.out.client.SnsIdentity
import org.around.ieuri.auth.application.port.out.client.SnsIdentityVerifierPort
import org.around.ieuri.auth.application.port.out.client.SnsTokenExchangerPort
import org.around.ieuri.auth.application.port.out.persistence.EmailAuthentication
import org.around.ieuri.auth.application.port.out.persistence.MemberAuthentication
import org.around.ieuri.auth.application.port.out.persistence.MemberRepositoryPort
import org.around.ieuri.auth.application.port.out.security.LoginIdentifierProtectorPort
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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** AuthTokenService — SNS 로그인·refresh 회전·로그아웃 오케스트레이션 검증. */
class AuthTokenServiceTest {

    private val snsVerifier = mockk<SnsIdentityVerifierPort>()
    private val memberRepository = mockk<MemberRepositoryPort>(relaxed = true)
    private val tokenProvider = mockk<TokenProviderPort>()
    private val refreshTokenStore = mockk<RefreshTokenStorePort>(relaxed = true)
    private val tokenIssuer = mockk<TokenIssuer>()
    private val passwordEncoder = mockk<PasswordEncoderPort>()

    /** 보호 변환 스텁: 결정론적이고 평문과 구분되게 접두사를 붙인다(실제 HMAC은 어댑터 관심사). */
    private val identifierProtector = object : LoginIdentifierProtectorPort {
        override fun protect(normalizedIdentifier: String) = "protected:$normalizedIdentifier"
    }

    private val tokenExchanger = mockk<SnsTokenExchangerPort>()

    // 교환·저장 로직이 실제로 실행되도록 목이 아니라 실제 인스턴스를 조립한다.
    private val providerTokenCollector = ProviderTokenCollector(tokenExchanger, memberRepository)

    private val now = Instant.parse("2026-07-17T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private val service = AuthTokenService(
        snsVerifier, memberRepository, tokenProvider, refreshTokenStore, tokenIssuer, passwordEncoder,
        identifierProtector, providerTokenCollector, clock,
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

    // --- loginBySns: provider 토큰 보충 (SPEC-0012 R5·R7) ---

    private fun givenActiveSnsMember() {
        every { snsVerifier.verify(any(), any()) } returns identity()
        every { memberRepository.findAuthentication(any(), any()) } returns
            MemberAuthentication(100L, MemberStatus.ACTIVE, null, null)
        every { tokenIssuer.issueFor(100L) } returns tokens
    }

    @Test
    fun `로그인에 authorizationCode가 있으면 교환한 refresh token을 저장한다`() {
        givenActiveSnsMember()
        every { tokenExchanger.exchangeForRefreshToken(Provider.GOOGLE, "auth-code-1") } returns "provider-refresh-1"

        assertEquals(tokens, service.loginBySns(loginCommand.copy(authorizationCode = "auth-code-1")))

        verify { memberRepository.updateProviderRefreshToken(100L, "provider-refresh-1") }
    }

    @Test
    fun `로그인에 authorizationCode가 없으면 교환도 저장도 시도하지 않는다`() {
        givenActiveSnsMember()

        assertEquals(tokens, service.loginBySns(loginCommand))

        verify(exactly = 0) { tokenExchanger.exchangeForRefreshToken(any(), any()) }
        verify(exactly = 0) { memberRepository.updateProviderRefreshToken(any(), any()) }
    }

    @Test
    fun `재로그인 시 새로 교환한 토큰으로 덮어쓴다`() {
        givenActiveSnsMember()
        every { tokenExchanger.exchangeForRefreshToken(Provider.GOOGLE, "auth-code-2") } returns "provider-refresh-2"

        service.loginBySns(loginCommand.copy(authorizationCode = "auth-code-2"))

        // 기존 값 확인 없이 그대로 갱신을 지시한다 — 덮어쓰기는 저장소 계약이다(R5).
        verify(exactly = 1) { memberRepository.updateProviderRefreshToken(100L, "provider-refresh-2") }
        verify(exactly = 0) { memberRepository.findProviderRefreshToken(any()) }
    }

    @Test
    fun `교환이 실패해도 로그인은 성공하고 토큰을 저장하지 않는다`() {
        givenActiveSnsMember()
        every { tokenExchanger.exchangeForRefreshToken(any(), any()) } throws RuntimeException("provider 장애")

        assertEquals(tokens, service.loginBySns(loginCommand.copy(authorizationCode = "auth-code-3")))

        verify(exactly = 0) { memberRepository.updateProviderRefreshToken(any(), any()) }
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
        every { memberRepository.findEmailAuthentication("protected:user@example.com") } returns emailAuth()
        every { passwordEncoder.matches("Passw0rd!", "hashed-pw") } returns true
        every { tokenIssuer.issueFor(100L) } returns tokens

        assertEquals(tokens, service.loginByEmail(emailLoginCommand))
        verify { tokenIssuer.issueFor(100L) }
    }

    @Test
    fun `이메일 조회는 평문이 아니라 보호된 식별자로 한다`() {
        val queried = slot<String>()
        every { memberRepository.findEmailAuthentication(capture(queried)) } returns emailAuth()
        every { passwordEncoder.matches(any(), any()) } returns true
        every { tokenIssuer.issueFor(100L) } returns tokens

        service.loginByEmail(emailLoginCommand)

        assertEquals("protected:user@example.com", queried.captured)
        assertNotEquals("user@example.com", queried.captured)
    }

    @Test
    fun `대소문자·공백만 다른 이메일도 같은 회원으로 판정한다`() {
        val queried = mutableListOf<String>()
        every { memberRepository.findEmailAuthentication(capture(queried)) } returns emailAuth()
        every { passwordEncoder.matches(any(), any()) } returns true
        every { tokenIssuer.issueFor(100L) } returns tokens

        service.loginByEmail(LoginEmailCommand("user@example.com", "Passw0rd!"))
        service.loginByEmail(LoginEmailCommand("  USER@Example.COM  ", "Passw0rd!"))

        // 정규화(Email VO) → 보호 변환 순서라 두 입력이 같은 조회 키가 된다.
        assertEquals(queried[0], queried[1])
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

    /**
     * 미인증은 로그인을 막지 않는다(SPEC-0024 R8) — 토큰은 정상 발급되고, 인증이 필요한
     * 오퍼레이션만 별도 게이트에서 걸린다(R9).
     */
    @Test
    fun `이메일 미인증 회원도 로그인에 성공한다`() {
        every { memberRepository.findEmailAuthentication(any()) } returns emailAuth(emailVerified = false)
        every { passwordEncoder.matches(any(), any()) } returns true
        every { tokenIssuer.issueFor(100L) } returns tokens

        assertEquals(tokens, service.loginByEmail(emailLoginCommand))
        verify { tokenIssuer.issueFor(100L) }
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
