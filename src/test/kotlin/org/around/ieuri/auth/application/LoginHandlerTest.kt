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
import org.around.ieuri.auth.domain.vo.MemberStatus
import org.around.ieuri.auth.domain.vo.Provider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** LoginHandler — SNS 인증 후 기존 회원 조회·상태 확인·토큰 발급 위임 검증. */
class LoginHandlerTest {

    private val snsVerifier = mockk<SnsIdentityVerifierPort>()
    private val memberRepository = mockk<MemberRepositoryPort>()
    private val tokenIssuer = mockk<TokenIssuer>()
    private val handler = LoginHandler(snsVerifier, memberRepository, tokenIssuer)

    private val tokens = TokenPairResult("acc", "ref", "Bearer", 300, 2_592_000)
    private val command = LoginCommand(Provider.GOOGLE, "dev-token")

    private fun identity() = SnsIdentity("google-sub-1", "u@example.com", true, "여행자", null)

    @Test
    fun `ACTIVE 회원이면 토큰을 발급한다`() {
        every { snsVerifier.verify(any(), any()) } returns identity()
        every { memberRepository.findAuthentication(any(), any()) } returns MemberAuthentication(100L, MemberStatus.ACTIVE)
        every { tokenIssuer.issueFor(100L) } returns tokens

        val result = handler.handle(command)

        assertEquals(tokens, result)
        verify { tokenIssuer.issueFor(100L) }
    }

    @Test
    fun `미가입 회원이면 MemberNotFoundException`() {
        every { snsVerifier.verify(any(), any()) } returns identity()
        every { memberRepository.findAuthentication(any(), any()) } returns null

        assertFailsWith<MemberNotFoundException> { handler.handle(command) }
        verify(exactly = 0) { tokenIssuer.issueFor(any()) }
    }

    @Test
    fun `ACTIVE가 아니면 InactiveMemberException`() {
        every { snsVerifier.verify(any(), any()) } returns identity()
        every { memberRepository.findAuthentication(any(), any()) } returns MemberAuthentication(100L, MemberStatus.SUSPENDED)

        assertFailsWith<InactiveMemberException> { handler.handle(command) }
        verify(exactly = 0) { tokenIssuer.issueFor(any()) }
    }
}
