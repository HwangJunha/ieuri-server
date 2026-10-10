package org.around.ieuri.auth.application.member

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.auth.application.member.provided.MemberAuthenticationUseCase
import org.around.ieuri.auth.application.member.required.SnsTokenExchangerPort
import org.around.ieuri.auth.domain.member.Provider
import kotlin.test.Test

/**
 * authorization code 교환·보관(SPEC-0012 R5·R7).
 * - 어떤 경우에도 예외를 밖으로 내보내지 않는다 — 가입·로그인을 막으면 안 된다.
 */
class ProviderTokenCollectorTest {

    private val tokenExchanger = mockk<SnsTokenExchangerPort>()
    private val authentication = mockk<MemberAuthenticationUseCase>(relaxUnitFun = true)

    private val collector = ProviderTokenCollector(tokenExchanger, authentication)

    @Test
    fun `교환에 성공하면 회원의 해제용 토큰으로 보관한다`() {
        every { tokenExchanger.exchangeForRefreshToken(Provider.APPLE, "code-1") } returns "apple-refresh-1"

        collector.collect(memberId = 100L, provider = Provider.APPLE, authorizationCode = "code-1")

        verify(exactly = 1) { authentication.keepProviderRefreshToken(100L, "apple-refresh-1") }
    }

    @Test
    fun `자체 회원은 교환 대상이 아니라 아무것도 하지 않는다`() {
        collector.collect(memberId = 100L, provider = Provider.EMAIL, authorizationCode = "code-1")

        verify(exactly = 0) { tokenExchanger.exchangeForRefreshToken(any(), any()) }
        verify(exactly = 0) { authentication.keepProviderRefreshToken(any(), any()) }
    }

    @Test
    fun `code가 없거나 공백뿐이면 교환하지 않는다`() {
        collector.collect(memberId = 100L, provider = Provider.GOOGLE, authorizationCode = null)
        collector.collect(memberId = 100L, provider = Provider.GOOGLE, authorizationCode = "   ")

        verify(exactly = 0) { tokenExchanger.exchangeForRefreshToken(any(), any()) }
        verify(exactly = 0) { authentication.keepProviderRefreshToken(any(), any()) }
    }

    /** 카카오는 어드민 키로 해제하므로 교환기가 null을 준다. */
    @Test
    fun `교환기가 토큰을 주지 않으면 보관하지 않는다`() {
        every { tokenExchanger.exchangeForRefreshToken(Provider.KAKAO, "code-1") } returns null

        collector.collect(memberId = 100L, provider = Provider.KAKAO, authorizationCode = "code-1")

        verify(exactly = 0) { authentication.keepProviderRefreshToken(any(), any()) }
    }

    @Test
    fun `교환이 예외로 실패해도 밖으로 내보내지 않고 보관도 하지 않는다`() {
        every { tokenExchanger.exchangeForRefreshToken(any(), any()) } throws IllegalStateException("provider 장애")

        collector.collect(memberId = 100L, provider = Provider.GOOGLE, authorizationCode = "code-1")

        verify(exactly = 0) { authentication.keepProviderRefreshToken(any(), any()) }
    }

    @Test
    fun `보관이 실패해도 밖으로 내보내지 않는다`() {
        every { tokenExchanger.exchangeForRefreshToken(any(), any()) } returns "google-refresh-1"
        every { authentication.keepProviderRefreshToken(any(), any()) } throws IllegalStateException("저장 실패")

        collector.collect(memberId = 100L, provider = Provider.GOOGLE, authorizationCode = "code-1")

        verify(exactly = 1) { authentication.keepProviderRefreshToken(100L, "google-refresh-1") }
    }
}
