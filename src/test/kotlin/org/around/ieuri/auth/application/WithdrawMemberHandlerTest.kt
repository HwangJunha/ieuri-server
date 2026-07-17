package org.around.ieuri.auth.application

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.auth.application.command.WithdrawMemberCommand
import org.around.ieuri.auth.application.exception.MemberNotFoundException
import org.around.ieuri.auth.application.port.out.client.SnsTokenRevokerPort
import org.around.ieuri.auth.application.port.out.event.DomainEventPublisherPort
import org.around.ieuri.auth.application.port.out.persistence.AuditLogPort
import org.around.ieuri.auth.application.port.out.persistence.MemberRepositoryPort
import org.around.ieuri.auth.application.port.out.token.RefreshTokenStorePort
import org.around.ieuri.auth.domain.aggregate.Member
import org.around.ieuri.auth.domain.event.MemberWithdrawn
import org.around.ieuri.auth.domain.vo.Email
import org.around.ieuri.auth.domain.vo.MemberStatus
import org.around.ieuri.auth.domain.vo.Nickname
import org.around.ieuri.auth.domain.vo.Provider
import org.around.ieuri.auth.domain.vo.ProviderUserId
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** WithdrawMemberHandler — 삭제·토큰 폐기·revoke·감사·이벤트 오케스트레이션 검증. */
class WithdrawMemberHandlerTest {

    private val memberRepository = mockk<MemberRepositoryPort>(relaxed = true)
    private val refreshTokenStore = mockk<RefreshTokenStorePort>(relaxed = true)
    private val snsTokenRevoker = mockk<SnsTokenRevokerPort>(relaxed = true)
    private val auditLog = mockk<AuditLogPort>(relaxed = true)
    private val eventPublisher = mockk<DomainEventPublisherPort>(relaxed = true)
    private val clock = Clock.fixed(Instant.parse("2026-07-17T00:00:00Z"), ZoneOffset.UTC)
    private val handler = WithdrawMemberHandler(memberRepository, refreshTokenStore, snsTokenRevoker, auditLog, eventPublisher, clock)

    private fun member() = Member.reconstitute(
        id = 100L, provider = Provider.APPLE, providerUserId = ProviderUserId("apple-sub-1"),
        email = Email("u@example.com"), emailVerified = true, status = MemberStatus.ACTIVE, ageVerified = true,
        suspendedAt = null, suspendedUntil = null, suspensionReason = null,
        nickname = Nickname("여행자"), profileImageUrl = null, createdAt = Instant.parse("2026-07-16T00:00:00Z"),
    )

    @Test
    fun `탈퇴 시 회원 삭제·토큰 폐기·revoke·감사·이벤트를 수행한다`() {
        every { memberRepository.load(100L) } returns member()

        val result = handler.handle(WithdrawMemberCommand(100L))

        assertTrue(result.success)
        verify { snsTokenRevoker.revoke(Provider.APPLE, "apple-sub-1") }
        verify { memberRepository.deleteMember(100L) }
        verify { refreshTokenStore.revokeAllByMember(100L) }
        verify { auditLog.record(100L, "MEMBER_WITHDRAWN", any()) }
        verify { eventPublisher.publish(any<MemberWithdrawn>()) }
    }

    @Test
    fun `회원이 없으면 예외를 던지고 삭제하지 않는다`() {
        every { memberRepository.load(100L) } returns null

        assertFailsWith<MemberNotFoundException> { handler.handle(WithdrawMemberCommand(100L)) }
        verify(exactly = 0) { memberRepository.deleteMember(any()) }
    }
}
