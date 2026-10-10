package org.around.ieuri.auth.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.auth.application.member.ProviderTokenCollector
import org.around.ieuri.auth.application.member.required.SnsTokenExchangerPort
import org.around.ieuri.auth.application.port.out.persistence.MemberRepositoryPort
import org.around.ieuri.auth.domain.member.Provider
import kotlin.test.Test

/**
 * ProviderTokenCollector — authorization code 교환·저장의 곁가지 동작 검증(SPEC-0012 R5·R7).
 * 어떤 경우에도 예외를 밖으로 내보내지 않는다는 것이 핵심 계약이다.
 */
class ProviderTokenCollectorTest {

    private val tokenExchanger = mockk<SnsTokenExchangerPort>()
    private val memberRepository = mockk<MemberRepositoryPort>(relaxed = true)

    private val collector = ProviderTokenCollector(tokenExchanger, memberRepository)

    @Test
    fun `교환에 성공하면 회원의 폐기용 토큰으로 저장한다`() {
        every { tokenExchanger.exchangeForRefreshToken(Provider.APPLE, "code-1") } returns "apple-refresh-1"

        collector.collect(memberId = 100L, provider = Provider.APPLE, authorizationCode = "code-1")

        verify { memberRepository.updateProviderRefreshToken(100L, "apple-refresh-1") }
    }

    @Test
    fun `EMAIL 회원은 교환 대상이 아니라 아무 것도 하지 않는다`() {
        collector.collect(memberId = 100L, provider = Provider.EMAIL, authorizationCode = "code-1")

        verify(exactly = 0) { tokenExchanger.exchangeForRefreshToken(any(), any()) }
        verify(exactly = 0) { memberRepository.updateProviderRefreshToken(any(), any()) }
    }

    @Test
    fun `code가 없으면 교환하지 않는다`() {
        collector.collect(memberId = 100L, provider = Provider.GOOGLE, authorizationCode = null)

        verify(exactly = 0) { tokenExchanger.exchangeForRefreshToken(any(), any()) }
        verify(exactly = 0) { memberRepository.updateProviderRefreshToken(any(), any()) }
    }

    @Test
    fun `code가 공백뿐이면 교환하지 않는다`() {
        collector.collect(memberId = 100L, provider = Provider.GOOGLE, authorizationCode = "   ")

        verify(exactly = 0) { tokenExchanger.exchangeForRefreshToken(any(), any()) }
        verify(exactly = 0) { memberRepository.updateProviderRefreshToken(any(), any()) }
    }

    @Test
    fun `교환기가 null을 주면(미발급·대상 아님) 저장하지 않는다`() {
        every { tokenExchanger.exchangeForRefreshToken(Provider.KAKAO, "code-1") } returns null

        collector.collect(memberId = 100L, provider = Provider.KAKAO, authorizationCode = "code-1")

        verify(exactly = 0) { memberRepository.updateProviderRefreshToken(any(), any()) }
    }

    @Test
    fun `교환기가 예외를 던져도 밖으로 전파하지 않고 저장도 하지 않는다`() {
        every { tokenExchanger.exchangeForRefreshToken(any(), any()) } throws RuntimeException("provider 장애")

        collector.collect(memberId = 100L, provider = Provider.GOOGLE, authorizationCode = "code-1")

        verify(exactly = 0) { memberRepository.updateProviderRefreshToken(any(), any()) }
    }

    @Test
    fun `저장이 실패해도 밖으로 전파하지 않는다`() {
        every { tokenExchanger.exchangeForRefreshToken(any(), any()) } returns "google-refresh-1"
        every { memberRepository.updateProviderRefreshToken(any(), any()) } throws RuntimeException("저장 실패")

        collector.collect(memberId = 100L, provider = Provider.GOOGLE, authorizationCode = "code-1")

        verify { memberRepository.updateProviderRefreshToken(100L, "google-refresh-1") }
    }
}
