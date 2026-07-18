package org.around.ieuri.auth.application

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.auth.application.command.AgreementInput
import org.around.ieuri.auth.application.command.RegisterMemberCommand
import org.around.ieuri.auth.application.event.VerificationEmailRequested
import org.around.ieuri.auth.application.exception.MemberAlreadyRegisteredException
import org.around.ieuri.auth.application.policy.VerificationPolicy
import org.around.ieuri.auth.application.port.out.client.SnsIdentity
import org.around.ieuri.auth.application.port.out.client.SnsIdentityVerifierPort
import org.around.ieuri.auth.application.port.out.event.DomainEventPublisherPort
import org.around.ieuri.auth.application.port.out.persistence.AuditLogPort
import org.around.ieuri.auth.application.port.out.persistence.EmailVerificationRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.MemberRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.RoleRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.TermsRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.TermsView
import org.around.ieuri.auth.application.port.out.security.VerificationCodeGeneratorPort
import org.around.ieuri.auth.domain.aggregate.EmailVerification
import org.around.ieuri.auth.domain.event.MemberRegistered
import org.around.ieuri.auth.domain.vo.Provider
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** RegisterMemberHandler 오케스트레이션 검증 — MockK로 아웃바운드 포트만 목킹(Spring 없음). */
class RegisterMemberHandlerTest {

    private val snsVerifier = mockk<SnsIdentityVerifierPort>()
    private val memberRepository = mockk<MemberRepositoryPort>()
    private val roleRepository = mockk<RoleRepositoryPort>(relaxed = true)
    private val termsRepository = mockk<TermsRepositoryPort>()
    private val verificationRepository = mockk<EmailVerificationRepositoryPort>(relaxed = true)
    private val codeGenerator = mockk<VerificationCodeGeneratorPort>()
    private val auditLog = mockk<AuditLogPort>(relaxed = true)
    private val eventPublisher = mockk<DomainEventPublisherPort>(relaxed = true)
    private val policy = VerificationPolicy(java.time.Duration.ofMinutes(10), 5, java.time.Duration.ofSeconds(60))
    private val clock = Clock.fixed(Instant.parse("2026-07-16T00:00:00Z"), ZoneOffset.UTC)

    private val handler = RegisterMemberHandler(
        snsVerifier, memberRepository, roleRepository, termsRepository, verificationRepository,
        codeGenerator, auditLog, eventPublisher, policy, clock,
    )

    private val activeTerms = listOf(
        TermsView(1, "SERVICE", "1.0", required = true),
        TermsView(2, "PRIVACY", "1.0", required = true),
        TermsView(3, "MARKETING", "1.0", required = false),
    )

    private fun identity(email: String? = "user@example.com", verified: Boolean = true) =
        SnsIdentity("google-sub-1", email, verified, "여행자", null)

    private fun command(email: String = "user@example.com") = RegisterMemberCommand(
        provider = Provider.GOOGLE,
        idToken = "dev-token",
        submittedEmail = email,
        ageVerified = true,
        agreements = listOf(
            AgreementInput("SERVICE", true),
            AgreementInput("PRIVACY", true),
            AgreementInput("MARKETING", false),
        ),
    )

    @Test
    fun `제출 이메일이 provider 값과 같으면 검증 없이 가입 성공한다`() {
        every { snsVerifier.verify(any(), any()) } returns identity()
        every { memberRepository.existsByProviderAndProviderUserId(any(), any()) } returns false
        every { termsRepository.findActiveTerms(any()) } returns activeTerms
        every { memberRepository.save(any()) } returns 100L

        val result = handler.handle(command())

        assertTrue(result.emailVerified)
        assertNull(result.verificationPublicId)
        verify(exactly = 1) { memberRepository.save(any()) }
        verify { roleRepository.assignRoleToMember(100L, "USER") }
        verify(exactly = 0) { verificationRepository.save(any()) }
        verify(exactly = 0) { codeGenerator.generate() }
        verify { auditLog.record(100L, "MEMBER_REGISTERED", any()) }
        verify { eventPublisher.publish(any<MemberRegistered>()) }
    }

    @Test
    fun `사용자가 이메일을 수정하면 미검증으로 시작하고 검증 코드를 발급한다`() {
        every { snsVerifier.verify(any(), any()) } returns identity(email = "original@example.com")
        every { memberRepository.existsByProviderAndProviderUserId(any(), any()) } returns false
        every { termsRepository.findActiveTerms(any()) } returns activeTerms
        every { memberRepository.save(any()) } returns 100L
        every { codeGenerator.generate() } returns "123456"
        every { codeGenerator.hash("123456") } returns "hashed"

        val result = handler.handle(command(email = "edited@example.com"))

        assertFalse(result.emailVerified)
        assertNotNull(result.verificationPublicId)
        verify(exactly = 1) { verificationRepository.save(any<EmailVerification>()) }
        verify { eventPublisher.publish(any<VerificationEmailRequested>()) }
    }

    @Test
    fun `이미 가입된 계정이면 예외를 던진다`() {
        every { snsVerifier.verify(any(), any()) } returns identity()
        every { memberRepository.existsByProviderAndProviderUserId(any(), any()) } returns true

        assertFailsWith<MemberAlreadyRegisteredException> { handler.handle(command()) }
        verify(exactly = 0) { memberRepository.save(any()) }
    }

    @Test
    fun `필수 약관 미동의면 도메인 불변식 위반으로 예외를 던진다`() {
        every { snsVerifier.verify(any(), any()) } returns identity()
        every { memberRepository.existsByProviderAndProviderUserId(any(), any()) } returns false
        every { termsRepository.findActiveTerms(any()) } returns activeTerms

        val cmd = RegisterMemberCommand(
            provider = Provider.GOOGLE,
            idToken = "dev-token",
            submittedEmail = "user@example.com",
            ageVerified = true,
            agreements = listOf(AgreementInput("SERVICE", true)), // PRIVACY 미동의
        )
        assertFailsWith<IllegalArgumentException> { handler.handle(cmd) }
        verify(exactly = 0) { memberRepository.save(any()) }
    }
}
