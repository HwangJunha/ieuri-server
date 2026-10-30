package org.around.ieuri.auth.application.member

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.around.ieuri.auth.application.member.provided.MemberCommandUseCase
import org.around.ieuri.auth.application.member.provided.dto.AgreementInput
import org.around.ieuri.auth.application.member.provided.dto.RegisterMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.RegisterMemberResult
import org.around.ieuri.auth.application.member.provided.dto.RegisterSnsIdentityCommand
import org.around.ieuri.auth.application.member.provided.dto.SnsRegistrationResult
import org.around.ieuri.auth.application.member.provided.exception.MemberAlreadyRegisteredException
import org.around.ieuri.auth.application.member.provided.exception.SnsVerificationFailedException
import org.around.ieuri.auth.application.member.required.SnsIdentityVerifierPort
import org.around.ieuri.auth.application.member.required.dto.SnsIdentity
import org.around.ieuri.auth.domain.member.Provider
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** SNS 가입의 순서: id_token 검증 → 가입(트랜잭션) → provider 토큰 확보. 외부 호출은 가입 트랜잭션 밖이다. */
class SnsRegistrationServiceTest {

    private val snsVerifier = mockk<SnsIdentityVerifierPort>()
    private val members = mockk<MemberCommandUseCase>()
    private val providerTokenCollector = mockk<ProviderTokenCollector>(relaxUnitFun = true)

    private val service = SnsRegistrationService(snsVerifier, members, providerTokenCollector)

    private val command = RegisterMemberCommand(
        provider = Provider.APPLE,
        idToken = "id-token",
        submittedEmail = "typed@example.com",
        ageVerified = true,
        agreements = listOf(AgreementInput("SERVICE", true)),
        authorizationCode = "auth-code-1",
        nickname = "클라이언트값",
    )
    private val identity = SnsIdentity(
        providerUserId = "apple-sub-1",
        email = "provider@example.com",
        nickname = "클레임값",
        profileImageUrl = "https://img.example/p.png",
    )
    private val registered = RegisterMemberResult(emailVerified = false, verificationPublicId = UUID.fromString("22222222-2222-2222-2222-222222222222"))

    @Test
    fun `검증한 신원으로 가입한 뒤에 provider 토큰을 확보하고 가입 결과를 그대로 돌려준다`() {
        every { snsVerifier.verify(Provider.APPLE, "id-token") } returns identity
        every { members.registerBySnsIdentity(any()) } returns SnsRegistrationResult(100L, registered)

        val result = service.registerBySns(command)

        assertEquals(registered, result)
        verifyOrder {
            snsVerifier.verify(Provider.APPLE, "id-token")
            members.registerBySnsIdentity(
                RegisterSnsIdentityCommand(
                    request = command,
                    providerUserId = "apple-sub-1",
                    email = "provider@example.com",
                    nickname = "클레임값",
                    profileImageUrl = "https://img.example/p.png",
                ),
            )
            providerTokenCollector.collect(100L, Provider.APPLE, "auth-code-1")
        }
    }

    @Test
    fun `id_token 검증이 실패하면 가입도 토큰 확보도 하지 않는다`() {
        every { snsVerifier.verify(any(), any()) } throws SnsVerificationFailedException("SNS 인증에 실패했습니다.")

        val ex = assertFailsWith<SnsVerificationFailedException> { service.registerBySns(command) }

        assertEquals("SNS 인증에 실패했습니다.", ex.message)
        verify(exactly = 0) { members.registerBySnsIdentity(any()) }
        verify(exactly = 0) { providerTokenCollector.collect(any(), any(), any()) }
    }

    @Test
    fun `가입이 거부되면 provider 토큰을 확보하지 않는다`() {
        every { snsVerifier.verify(any(), any()) } returns identity
        every { members.registerBySnsIdentity(any()) } throws MemberAlreadyRegisteredException("이미 가입된 계정입니다.")

        assertFailsWith<MemberAlreadyRegisteredException> { service.registerBySns(command) }

        verify(exactly = 0) { providerTokenCollector.collect(any(), any(), any()) }
    }
}
