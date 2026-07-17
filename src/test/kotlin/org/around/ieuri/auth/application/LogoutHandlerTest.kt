package org.around.ieuri.auth.application

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.auth.application.command.LogoutCommand
import org.around.ieuri.auth.application.exception.InvalidTokenException
import org.around.ieuri.auth.application.port.out.token.RefreshTokenClaims
import org.around.ieuri.auth.application.port.out.token.RefreshTokenStorePort
import org.around.ieuri.auth.application.port.out.token.TokenProviderPort
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertTrue

/** LogoutHandler — refresh 폐기 및 무효 토큰 멱등 처리 검증. */
class LogoutHandlerTest {

    private val tokenProvider = mockk<TokenProviderPort>()
    private val refreshTokenStore = mockk<RefreshTokenStorePort>(relaxed = true)
    private val handler = LogoutHandler(tokenProvider, refreshTokenStore)

    @Test
    fun `유효한 refresh면 해당 jti를 폐기한다`() {
        every { tokenProvider.parseRefreshToken("ref") } returns
            RefreshTokenClaims(100L, "jti-1", Instant.parse("2026-08-16T00:00:00Z"))

        val result = handler.handle(LogoutCommand("ref"))

        assertTrue(result.success)
        verify { refreshTokenStore.revoke("jti-1") }
    }

    @Test
    fun `무효한 토큰이어도 멱등하게 성공 처리하고 폐기하지 않는다`() {
        every { tokenProvider.parseRefreshToken("bad") } throws InvalidTokenException("invalid")

        val result = handler.handle(LogoutCommand("bad"))

        assertTrue(result.success)
        verify(exactly = 0) { refreshTokenStore.revoke(any()) }
    }
}
