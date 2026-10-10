package org.around.ieuri.auth.application.member

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.mockk.verifyOrder
import org.around.ieuri.auth.application.member.provided.dto.EraseMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.ReactivateMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.SuspendMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.WithdrawalTargetView
import org.around.ieuri.auth.application.member.provided.exception.MemberNotFoundException
import org.around.ieuri.auth.application.member.required.MemberEventPublisherPort
import org.around.ieuri.auth.application.member.required.MemberRepository
import org.around.ieuri.auth.application.member.required.MemberSessionPort
import org.around.ieuri.auth.domain.member.InvalidSuspensionException
import org.around.ieuri.auth.domain.member.MemberStatus
import org.around.ieuri.auth.domain.member.NotSuspendedException
import org.around.ieuri.auth.domain.member.Provider
import org.around.ieuri.auth.domain.member.event.MemberReactivated
import org.around.ieuri.auth.domain.member.event.MemberSuspended
import org.around.ieuri.auth.domain.member.event.MemberWithdrawn
import org.around.ieuri.auth.domain.member.event.SnsUnlinkFailed
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 정지·복구와 탈퇴의 DB 단계. 상태 전이는 도메인이 판정하고, 서비스는 저장 → 세션 폐기 → 이벤트 순서를 지킨다. */
class MemberLifecycleServiceTest {

    private val now = Instant.parse("2026-07-17T00:00:00Z")

    private val memberRepository = mockk<MemberRepository>(relaxUnitFun = true)
    private val sessions = mockk<MemberSessionPort>(relaxUnitFun = true)
    private val eventPublisher = mockk<MemberEventPublisherPort>()
    private val events = mutableListOf<Any>()

    private val service = MemberLifecycleService(memberRepository, sessions, eventPublisher, Clock.fixed(now, ZoneOffset.UTC))

    init {
        every { eventPublisher.publish(capture(events)) } just runs
    }

    private fun suspendedMember() = memberFixture(
        provider = Provider.GOOGLE,
        status = MemberStatus.SUSPENDED,
        suspendedAt = now.minusSeconds(10),
        suspendedUntil = now.plusSeconds(3600),
        suspensionReason = "사유",
    )

    // --- 정지 ---

    /** 정지가 정해진 뒤에만 세션을 끊는다. */
    @Test
    fun `정지는 상태 저장 → 세션 폐기 → 이벤트 순으로 한다`() {
        val until = now.plusSeconds(86_400)
        every { memberRepository.findById(100L) } returns memberFixture(provider = Provider.GOOGLE)

        val result = service.suspend(SuspendMemberCommand(100L, "약관 위반", until))

        assertTrue(result.success)
        val suspended = memberFixture(
            provider = Provider.GOOGLE,
            status = MemberStatus.SUSPENDED,
            suspendedAt = now,
            suspendedUntil = until,
            suspensionReason = "약관 위반",
        )
        verifyOrder {
            memberRepository.saveStatus(suspended)
            sessions.revokeAll(100L)
            eventPublisher.publish(MemberSuspended(100L, "약관 위반", until, now))
        }
        assertEquals(listOf<Any>(MemberSuspended(100L, "약관 위반", until, now)), events)
    }

    @Test
    fun `도메인이 정지를 거부하면 저장도 세션 폐기도 하지 않는다`() {
        every { memberRepository.findById(100L) } returns memberFixture()

        val ex = assertFailsWith<InvalidSuspensionException> { service.suspend(SuspendMemberCommand(100L, "약관 위반", now)) }

        assertEquals("정지 만료일은 현재 이후여야 합니다.", ex.message)
        verify(exactly = 0) { memberRepository.saveStatus(any()) }
        verify(exactly = 0) { sessions.revokeAll(any()) }
        assertTrue(events.isEmpty())
    }

    @Test
    fun `정지 대상 회원이 없으면 회원 없음으로 답하고 세션을 건드리지 않는다`() {
        every { memberRepository.findById(100L) } returns null

        val ex = assertFailsWith<MemberNotFoundException> { service.suspend(SuspendMemberCommand(100L, "사유", null)) }

        assertEquals("회원을 찾을 수 없습니다.", ex.message)
        verify(exactly = 0) { sessions.revokeAll(any()) }
        assertTrue(events.isEmpty())
    }

    /** 폐기가 실패하면 예외가 나가 정지도 되돌아간다 — 이벤트는 내지 않는다. */
    @Test
    fun `세션 폐기가 실패하면 예외를 그대로 내보내고 정지 이벤트를 내지 않는다`() {
        every { memberRepository.findById(100L) } returns memberFixture()
        every { sessions.revokeAll(100L) } throws IllegalStateException("세션 저장소 장애")

        assertFailsWith<IllegalStateException> { service.suspend(SuspendMemberCommand(100L, "사유", null)) }

        assertTrue(events.isEmpty())
    }

    // --- 복구 ---

    @Test
    fun `복구는 정지 정보를 비운 ACTIVE 상태를 저장하고 이벤트를 낸다`() {
        every { memberRepository.findById(100L) } returns suspendedMember()

        val result = service.reactivate(ReactivateMemberCommand(100L))

        assertTrue(result.success)
        verify(exactly = 1) { memberRepository.saveStatus(memberFixture(provider = Provider.GOOGLE, status = MemberStatus.ACTIVE)) }
        // 복구는 세션을 건드리지 않는다.
        verify(exactly = 0) { sessions.revokeAll(any()) }
        assertEquals(listOf<Any>(MemberReactivated(100L, now)), events)
    }

    @Test
    fun `정지 상태가 아닌 회원의 복구는 거부하고 아무것도 쓰지 않는다`() {
        every { memberRepository.findById(100L) } returns memberFixture(status = MemberStatus.ACTIVE)

        val ex = assertFailsWith<NotSuspendedException> { service.reactivate(ReactivateMemberCommand(100L)) }

        assertEquals("정지 상태에서만 복구할 수 있습니다.", ex.message)
        verify(exactly = 0) { memberRepository.saveStatus(any()) }
        assertTrue(events.isEmpty())
    }

    @Test
    fun `복구 대상 회원이 없으면 회원 없음으로 답한다`() {
        every { memberRepository.findById(100L) } returns null

        val ex = assertFailsWith<MemberNotFoundException> { service.reactivate(ReactivateMemberCommand(100L)) }

        assertEquals("회원을 찾을 수 없습니다.", ex.message)
    }

    // --- 탈퇴 대상 ---

    @Test
    fun `탈퇴 대상은 provider와 식별자와 저장된 해제용 토큰을 담는다`() {
        every { memberRepository.findById(100L) } returns memberFixture(provider = Provider.APPLE, providerUserId = "apple-sub-1")
        every { memberRepository.findProviderRefreshToken(100L) } returns "apple-refresh-1"

        val target = service.withdrawalTarget(100L)

        assertEquals(WithdrawalTargetView(Provider.APPLE, "apple-sub-1", "apple-refresh-1"), target)
    }

    @Test
    fun `저장된 해제용 토큰이 없으면 토큰 없이 돌려준다`() {
        every { memberRepository.findById(100L) } returns memberFixture(provider = Provider.KAKAO, providerUserId = "kakao-9911")
        every { memberRepository.findProviderRefreshToken(100L) } returns null

        assertEquals(WithdrawalTargetView(Provider.KAKAO, "kakao-9911", null), service.withdrawalTarget(100L))
    }

    @Test
    fun `탈퇴 대상 회원이 없으면 토큰을 읽지 않고 회원 없음으로 답한다`() {
        every { memberRepository.findById(100L) } returns null

        val ex = assertFailsWith<MemberNotFoundException> { service.withdrawalTarget(100L) }

        assertEquals("회원을 찾을 수 없습니다.", ex.message)
        verify(exactly = 0) { memberRepository.findProviderRefreshToken(any()) }
    }

    // --- 삭제 ---

    @Test
    fun `삭제는 회원을 지운 뒤 탈퇴 사실을 provider와 함께 알린다`() {
        service.erase(EraseMemberCommand(100L, Provider.GOOGLE, unlinkFailureReason = null))

        verifyOrder {
            memberRepository.delete(100L)
            eventPublisher.publish(MemberWithdrawn(100L, Provider.GOOGLE, now))
        }
        // 해제가 실패하지 않았으면 해제 실패를 알리지 않는다.
        assertEquals(listOf<Any>(MemberWithdrawn(100L, Provider.GOOGLE, now)), events)
    }

    /** 해제 실패는 provider와 사유만 남긴다(R11). */
    @Test
    fun `해제 실패 사유가 있으면 해제 실패와 탈퇴를 차례로 알린다`() {
        service.erase(EraseMemberCommand(100L, Provider.APPLE, unlinkFailureReason = "HTTP 400 invalid_grant"))

        verify(exactly = 1) { memberRepository.delete(100L) }
        assertEquals(
            listOf(
                SnsUnlinkFailed(100L, Provider.APPLE, "HTTP 400 invalid_grant"),
                MemberWithdrawn(100L, Provider.APPLE, now),
            ),
            events,
        )
    }
}
