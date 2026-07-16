package org.around.ieuri.auth.application

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.auth.application.command.VerifyEmailCommand
import org.around.ieuri.auth.application.exception.VerificationNotFoundException
import org.around.ieuri.auth.application.port.out.event.DomainEventPublisherPort
import org.around.ieuri.auth.application.port.out.persistence.AuditLogPort
import org.around.ieuri.auth.application.port.out.persistence.EmailVerificationRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.MemberRepositoryPort
import org.around.ieuri.auth.application.port.out.security.VerificationCodeGeneratorPort
import org.around.ieuri.auth.domain.aggregate.EmailVerification
import org.around.ieuri.auth.domain.event.EmailVerified
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** VerifyEmailHandler — 성공/실패 분기와 부수효과(멤버 갱신·감사·이벤트) 검증. */
class VerifyEmailHandlerTest {

    private val verificationRepository = mockk<EmailVerificationRepositoryPort>(relaxed = true)
    private val memberRepository = mockk<MemberRepositoryPort>(relaxed = true)
    private val codeGenerator = mockk<VerificationCodeGeneratorPort>()
    private val auditLog = mockk<AuditLogPort>(relaxed = true)
    private val eventPublisher = mockk<DomainEventPublisherPort>(relaxed = true)
    private val clock = Clock.fixed(Instant.parse("2026-07-16T00:00:00Z"), ZoneOffset.UTC)

    private val handler = VerifyEmailHandler(
        verificationRepository, memberRepository, codeGenerator, auditLog, eventPublisher, clock,
    )

    private val publicId = UUID.randomUUID()

    private fun challenge(codeHash: String = "correct") = EmailVerification.issue(
        publicId = publicId,
        memberId = 100L,
        codeHash = codeHash,
        ttl = Duration.ofMinutes(10),
        now = Instant.parse("2026-07-16T00:00:00Z"),
    )

    @Test
    fun `올바른 코드면 검증 성공하고 회원을 검증 완료로 갱신한다`() {
        every { verificationRepository.findByPublicId(publicId) } returns challenge(codeHash = "correct")
        every { codeGenerator.hash("123456") } returns "correct"

        val result = handler.handle(VerifyEmailCommand(publicId, "123456"))

        assertTrue(result.verified)
        verify { verificationRepository.update(any()) }
        verify { memberRepository.markEmailVerified(100L, any()) }
        verify { auditLog.record(100L, "EMAIL_VERIFIED", any()) }
        verify { eventPublisher.publish(any<EmailVerified>()) }
    }

    @Test
    fun `틀린 코드면 실패하고 회원을 갱신하지 않으며 attempts 반영을 위해 update한다`() {
        every { verificationRepository.findByPublicId(publicId) } returns challenge(codeHash = "correct")
        every { codeGenerator.hash("000000") } returns "wrong"

        val result = handler.handle(VerifyEmailCommand(publicId, "000000"))

        assertFalse(result.verified)
        assertEquals("CODE_MISMATCH", result.failureReason)
        verify { verificationRepository.update(any()) }
        verify(exactly = 0) { memberRepository.markEmailVerified(any(), any()) }
    }

    @Test
    fun `핸들에 해당하는 챌린지가 없으면 예외를 던진다`() {
        every { verificationRepository.findByPublicId(publicId) } returns null

        assertFailsWith<VerificationNotFoundException> {
            handler.handle(VerifyEmailCommand(publicId, "123456"))
        }
    }
}
