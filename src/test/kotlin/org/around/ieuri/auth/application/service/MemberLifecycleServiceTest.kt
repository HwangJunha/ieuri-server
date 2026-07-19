package org.around.ieuri.auth.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.auth.application.command.ReactivateMemberCommand
import org.around.ieuri.auth.application.command.SuspendMemberCommand
import org.around.ieuri.auth.application.command.WithdrawMemberCommand
import org.around.ieuri.auth.application.exception.MemberNotFoundException
import org.around.ieuri.auth.application.port.out.client.SnsTokenRevokerPort
import org.around.ieuri.auth.application.port.out.event.DomainEventPublisherPort
import org.around.ieuri.auth.application.port.out.persistence.AuditLogPort
import org.around.ieuri.auth.application.port.out.persistence.MemberRepositoryPort
import org.around.ieuri.auth.application.port.out.token.RefreshTokenStorePort
import org.around.ieuri.auth.domain.aggregate.Member
import org.around.ieuri.auth.domain.event.MemberReactivated
import org.around.ieuri.auth.domain.event.MemberSuspended
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

/** MemberLifecycleService — 탈퇴·정지·복구 상태 전이 오케스트레이션 검증. */
class MemberLifecycleServiceTest {

    private val memberRepository = mockk<MemberRepositoryPort>(relaxed = true)
    private val refreshTokenStore = mockk<RefreshTokenStorePort>(relaxed = true)
    private val snsTokenRevoker = mockk<SnsTokenRevokerPort>(relaxed = true)
    private val auditLog = mockk<AuditLogPort>(relaxed = true)
    private val eventPublisher = mockk<DomainEventPublisherPort>(relaxed = true)
    private val now = Instant.parse("2026-07-17T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private val service = MemberLifecycleService(
        memberRepository, refreshTokenStore, snsTokenRevoker, auditLog, eventPublisher, clock,
    )

    private fun member(status: MemberStatus, provider: Provider = Provider.GOOGLE, providerUserId: String = "g1") = Member.reconstitute(
        id = 100L, provider = provider, providerUserId = ProviderUserId(providerUserId),
        email = Email("u@example.com"), emailVerified = true, status = status, ageVerified = true,
        suspendedAt = if (status == MemberStatus.SUSPENDED) now.minusSeconds(10) else null,
        suspendedUntil = null,
        suspensionReason = if (status == MemberStatus.SUSPENDED) "사유" else null,
        nickname = Nickname("여행자"), profileImageUrl = null, createdAt = now.minusSeconds(1000),
    )

    // --- withdraw ---

    @Test
    fun `탈퇴 시 회원 삭제·토큰 폐기·revoke·감사·이벤트를 수행한다`() {
        every { memberRepository.load(100L) } returns member(MemberStatus.ACTIVE, provider = Provider.APPLE, providerUserId = "apple-sub-1")

        val result = service.withdraw(WithdrawMemberCommand(100L))

        assertTrue(result.success)
        verify { snsTokenRevoker.revoke(Provider.APPLE, "apple-sub-1") }
        verify { memberRepository.deleteMember(100L) }
        verify { refreshTokenStore.revokeAllByMember(100L) }
        verify { auditLog.record(100L, "MEMBER_WITHDRAWN", any()) }
        verify { eventPublisher.publish(any<MemberWithdrawn>()) }
    }

    @Test
    fun `탈퇴 대상 회원이 없으면 예외를 던지고 삭제하지 않는다`() {
        every { memberRepository.load(100L) } returns null

        assertFailsWith<MemberNotFoundException> { service.withdraw(WithdrawMemberCommand(100L)) }
        verify(exactly = 0) { memberRepository.deleteMember(any()) }
    }

    // --- suspend ---

    @Test
    fun `정지하면 상태 반영·토큰 폐기·감사·이벤트를 수행한다`() {
        every { memberRepository.load(100L) } returns member(MemberStatus.ACTIVE)

        val result = service.suspend(SuspendMemberCommand(100L, "약관 위반", null))

        assertTrue(result.success)
        verify { memberRepository.updateStatus(any()) }
        verify { refreshTokenStore.revokeAllByMember(100L) }
        verify { auditLog.record(100L, "MEMBER_SUSPENDED", any()) }
        verify { eventPublisher.publish(any<MemberSuspended>()) }
    }

    @Test
    fun `정지 대상 회원이 없으면 예외`() {
        every { memberRepository.load(100L) } returns null
        assertFailsWith<MemberNotFoundException> { service.suspend(SuspendMemberCommand(100L, "사유", null)) }
    }

    // --- reactivate ---

    @Test
    fun `복구하면 ACTIVE로 전이·감사·이벤트를 수행한다`() {
        every { memberRepository.load(100L) } returns member(MemberStatus.SUSPENDED)

        val result = service.reactivate(ReactivateMemberCommand(100L))

        assertTrue(result.success)
        verify { memberRepository.updateStatus(any()) }
        verify { auditLog.record(100L, "MEMBER_REACTIVATED", any()) }
        verify { eventPublisher.publish(any<MemberReactivated>()) }
    }

    @Test
    fun `ACTIVE 회원을 복구하려 하면 도메인 불변식 위반으로 예외`() {
        every { memberRepository.load(100L) } returns member(MemberStatus.ACTIVE)
        assertFailsWith<IllegalArgumentException> { service.reactivate(ReactivateMemberCommand(100L)) }
        verify(exactly = 0) { memberRepository.updateStatus(any()) }
    }
}
