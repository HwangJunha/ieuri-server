package org.around.ieuri.auth.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.around.ieuri.auth.application.command.ReactivateMemberCommand
import org.around.ieuri.auth.application.command.SuspendMemberCommand
import org.around.ieuri.auth.application.command.WithdrawMemberCommand
import org.around.ieuri.auth.application.exception.MemberNotFoundException
import org.around.ieuri.auth.application.port.out.client.RevokeSnsConnectionCommand
import org.around.ieuri.auth.application.port.out.client.SnsRevokeResult
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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
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
        memberRepository,
        refreshTokenStore,
        snsTokenRevoker,
        auditLog,
        eventPublisher,
        clock,
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

    /** 탈퇴 대상 회원과 저장된 폐기용 토큰을 준비한다. token=null이면 저장된 토큰이 없는 회원. */
    private fun givenWithdrawable(
        provider: Provider,
        providerUserId: String,
        token: String?,
        revokeResult: SnsRevokeResult = SnsRevokeResult.Revoked,
    ) {
        every { memberRepository.load(100L) } returns member(MemberStatus.ACTIVE, provider, providerUserId)
        every { memberRepository.findProviderRefreshToken(100L) } returns token
        every { snsTokenRevoker.revoke(any()) } returns revokeResult
    }

    @Test
    fun `탈퇴 시 회원 삭제·토큰 폐기·revoke·감사·이벤트를 수행한다`() {
        givenWithdrawable(Provider.APPLE, "apple-sub-1", token = "apple-refresh-1")

        val result = service.withdraw(WithdrawMemberCommand(100L))

        assertTrue(result.success)
        verify { snsTokenRevoker.revoke(any()) }
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

    // --- withdraw: provider 연결 해제 (SPEC-0012 R8~R11) ---

    @Test
    fun `애플 회원은 저장된 토큰과 함께 해제를 요청한다`() {
        givenWithdrawable(Provider.APPLE, "apple-sub-1", token = "apple-refresh-1")
        val revoked = slot<RevokeSnsConnectionCommand>()
        every { snsTokenRevoker.revoke(capture(revoked)) } returns SnsRevokeResult.Revoked

        service.withdraw(WithdrawMemberCommand(100L))

        assertEquals(Provider.APPLE, revoked.captured.provider)
        assertEquals("apple-sub-1", revoked.captured.providerUserId)
        assertEquals("apple-refresh-1", revoked.captured.providerRefreshToken)
    }

    @Test
    fun `구글 회원은 저장된 토큰과 함께 해제를 요청한다`() {
        givenWithdrawable(Provider.GOOGLE, "google-sub-1", token = "google-refresh-1")
        val revoked = slot<RevokeSnsConnectionCommand>()
        every { snsTokenRevoker.revoke(capture(revoked)) } returns SnsRevokeResult.Revoked

        service.withdraw(WithdrawMemberCommand(100L))

        assertEquals(Provider.GOOGLE, revoked.captured.provider)
        assertEquals("google-sub-1", revoked.captured.providerUserId)
        assertEquals("google-refresh-1", revoked.captured.providerRefreshToken)
    }

    @Test
    fun `저장된 토큰이 없으면 토큰 없이 넘겨 Skipped로 끝나고 탈퇴는 완료된다`() {
        givenWithdrawable(Provider.APPLE, "apple-sub-1", token = null, revokeResult = SnsRevokeResult.Skipped)
        val revoked = slot<RevokeSnsConnectionCommand>()
        every { snsTokenRevoker.revoke(capture(revoked)) } returns SnsRevokeResult.Skipped

        val result = service.withdraw(WithdrawMemberCommand(100L))

        assertTrue(result.success)
        assertNull(revoked.captured.providerRefreshToken)
        verify { memberRepository.deleteMember(100L) }
        // Skipped는 실패가 아니므로 해제 실패 감사를 남기지 않는다.
        verify(exactly = 0) { auditLog.record(any(), "SNS_UNLINK_FAILED", any()) }
    }

    @Test
    fun `카카오 회원은 토큰 없이 providerUserId를 대상으로 해제를 요청한다`() {
        givenWithdrawable(Provider.KAKAO, "kakao-9911", token = null)
        val revoked = slot<RevokeSnsConnectionCommand>()
        every { snsTokenRevoker.revoke(capture(revoked)) } returns SnsRevokeResult.Revoked

        service.withdraw(WithdrawMemberCommand(100L))

        assertEquals(Provider.KAKAO, revoked.captured.provider)
        assertEquals("kakao-9911", revoked.captured.providerUserId)
        assertNull(revoked.captured.providerRefreshToken)
    }

    @Test
    fun `해제 요청은 회원의 provider 하나로만 나가고 다른 provider로는 나가지 않는다`() {
        givenWithdrawable(Provider.KAKAO, "kakao-9911", token = null)
        val revoked = mutableListOf<RevokeSnsConnectionCommand>()
        every { snsTokenRevoker.revoke(capture(revoked)) } returns SnsRevokeResult.Revoked

        service.withdraw(WithdrawMemberCommand(100L))

        assertEquals(listOf(Provider.KAKAO), revoked.map { it.provider })
    }

    @Test
    fun `해제 대상이 아닌 EMAIL 회원도 탈퇴는 정상 완료된다`() {
        givenWithdrawable(Provider.EMAIL, "protected-identifier", token = null, revokeResult = SnsRevokeResult.Skipped)

        val result = service.withdraw(WithdrawMemberCommand(100L))

        assertTrue(result.success)
        verify { memberRepository.deleteMember(100L) }
        verify(exactly = 0) { auditLog.record(any(), "SNS_UNLINK_FAILED", any()) }
    }

    @Test
    fun `해제가 실패해도 탈퇴는 완료되고 provider와 사유만 감사에 남긴다`() {
        givenWithdrawable(
            Provider.APPLE,
            "apple-sub-1",
            token = "apple-refresh-1",
            revokeResult = SnsRevokeResult.Failed("HTTP 400 invalid_grant"),
        )
        val payload = slot<Map<String, Any?>>()
        every { auditLog.record(100L, "SNS_UNLINK_FAILED", capture(payload)) } returns Unit

        val result = service.withdraw(WithdrawMemberCommand(100L))

        assertTrue(result.success)
        verify { memberRepository.deleteMember(100L) }
        verify { refreshTokenStore.revokeAllByMember(100L) }
        verify { eventPublisher.publish(any<MemberWithdrawn>()) }

        assertEquals("APPLE", payload.captured["provider"])
        assertEquals("HTTP 400 invalid_grant", payload.captured["reason"])
        // 토큰·이메일은 감사에 남기지 않는다(R11).
        val recorded = payload.captured.values.map { it.toString() }
        assertTrue(recorded.none { it.contains("apple-refresh-1") })
        assertTrue(recorded.none { it.contains("u@example.com") })
    }

    @Test
    fun `해제 성공이면 해제 실패 감사를 남기지 않는다`() {
        givenWithdrawable(Provider.GOOGLE, "google-sub-1", token = "google-refresh-1")

        service.withdraw(WithdrawMemberCommand(100L))

        verify(exactly = 0) { auditLog.record(any(), "SNS_UNLINK_FAILED", any()) }
        verify { auditLog.record(100L, "MEMBER_WITHDRAWN", any()) }
    }

    @Test
    fun `해제용 토큰은 회원 삭제 전에 읽는다`() {
        givenWithdrawable(Provider.APPLE, "apple-sub-1", token = "apple-refresh-1")

        service.withdraw(WithdrawMemberCommand(100L))

        // 회원 행이 지워진 뒤에는 토큰을 읽을 수 없으므로 순서가 계약이다.
        verifyOrder {
            memberRepository.findProviderRefreshToken(100L)
            snsTokenRevoker.revoke(any())
            memberRepository.deleteMember(100L)
        }
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
