package org.around.ieuri.auth.application

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.auth.application.command.RefreshTokenCommand
import org.around.ieuri.auth.application.exception.InvalidTokenException
import org.around.ieuri.auth.application.exception.TokenReuseDetectedException
import org.around.ieuri.auth.application.port.`in`.command.TokenPairResult
import org.around.ieuri.auth.application.port.out.token.RefreshTokenClaims
import org.around.ieuri.auth.application.port.out.token.RefreshTokenStorePort
import org.around.ieuri.auth.application.port.out.token.TokenProviderPort
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** RefreshTokenHandler — 회전과 재사용 감지 검증. */
class RefreshTokenHandlerTest {

    private val tokenProvider = mockk<TokenProviderPort>()
    private val refreshTokenStore = mockk<RefreshTokenStorePort>(relaxed = true)
    private val tokenIssuer = mockk<TokenIssuer>()
    private val handler = RefreshTokenHandler(tokenProvider, refreshTokenStore, tokenIssuer)

    private val tokens = TokenPairResult("newAcc", "newRef", "Bearer", 300, 2_592_000)
    private val claims = RefreshTokenClaims(memberId = 100L, jti = "jti-1", expiresAt = Instant.parse("2026-08-16T00:00:00Z"))

    @Test
    fun `저장소에 살아있는 refresh면 회전(이전 폐기 + 재발급)한다`() {
        every { tokenProvider.parseRefreshToken("ref") } returns claims
        every { refreshTokenStore.existsActive("jti-1") } returns true
        every { tokenIssuer.issueFor(100L) } returns tokens

        val result = handler.handle(RefreshTokenCommand("ref"))

        assertEquals(tokens, result)
        verify { refreshTokenStore.revoke("jti-1") }
        verify { tokenIssuer.issueFor(100L) }
    }

    @Test
    fun `이미 폐기된 refresh 재사용이면 회원 전체 폐기 후 TokenReuseDetectedException`() {
        every { tokenProvider.parseRefreshToken("ref") } returns claims
        every { refreshTokenStore.existsActive("jti-1") } returns false

        assertFailsWith<TokenReuseDetectedException> { handler.handle(RefreshTokenCommand("ref")) }
        verify { refreshTokenStore.revokeAllByMember(100L) }
        verify(exactly = 0) { tokenIssuer.issueFor(any()) }
    }

    @Test
    fun `유효하지 않은 토큰이면 InvalidTokenException이 전파된다`() {
        every { tokenProvider.parseRefreshToken("bad") } throws InvalidTokenException("invalid")

        assertFailsWith<InvalidTokenException> { handler.handle(RefreshTokenCommand("bad")) }
        verify(exactly = 0) { tokenIssuer.issueFor(any()) }
    }
}
