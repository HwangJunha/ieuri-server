package org.around.ieuri.auth.application.member

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.mockk.verifyOrder
import org.around.ieuri.auth.application.member.provided.MemberCommandUseCase
import org.around.ieuri.auth.application.member.provided.dto.WithdrawalTargetView
import org.around.ieuri.auth.application.member.provided.exception.MemberNotFoundException
import org.around.ieuri.auth.application.member.required.MemberEventPublisherPort
import org.around.ieuri.auth.application.member.required.MemberSessionPort
import org.around.ieuri.auth.application.member.required.SnsTokenRevokerPort
import org.around.ieuri.auth.application.member.required.dto.RevokeSnsConnectionCommand
import org.around.ieuri.auth.application.member.required.dto.SnsRevokeResult
import org.around.ieuri.auth.domain.member.Provider
import org.around.ieuri.common.event.AccessType
import org.around.ieuri.common.event.MemberAccessed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 탈퇴(SPEC-0012 R8~R11)의 순서: 대상 조회 → provider 해제 → 세션 폐기 → 삭제.
 * - 해제용 토큰이 회원 행에 있어 삭제 뒤에는 읽을 수 없고, 세션 폐기가 실패하면 아무것도 지우지 않는다.
 */
class MemberWithdrawalServiceTest {

    private val members = mockk<MemberCommandUseCase>(relaxUnitFun = true)
    private val snsTokenRevoker = mockk<SnsTokenRevokerPort>()
    private val sessions = mockk<MemberSessionPort>(relaxUnitFun = true)
    private val eventPublisher = mockk<MemberEventPublisherPort>()
    private val events = mutableListOf<Any>()

    private val service = MemberWithdrawalService(members, snsTokenRevoker, sessions, eventPublisher)

    init {
        every { eventPublisher.publish(capture(events)) } just runs
    }

    private fun givenTarget(
        provider: Provider = Provider.APPLE,
        providerUserId: String = "apple-sub-1",
        token: String? = "apple-refresh-1",
        revokeResult: SnsRevokeResult = SnsRevokeResult.Revoked,
    ) {
        every { members.withdrawalTarget(100L) } returns WithdrawalTargetView(provider, providerUserId, token)
        every { snsTokenRevoker.revoke(any()) } returns revokeResult
    }

    @Test
    fun `탈퇴는 대상 조회 → provider 해제 → 세션 폐기 → 삭제 → 접속기록 순으로 한다`() {
        givenTarget()

        val result = service.withdraw(100L, "203.0.113.7")

        assertTrue(result.success)
        verifyOrder {
            members.withdrawalTarget(100L)
            snsTokenRevoker.revoke(RevokeSnsConnectionCommand(Provider.APPLE, "apple-sub-1", "apple-refresh-1"))
            sessions.revokeAll(100L)
            members.erase(100L, Provider.APPLE, unlinkFailureReason = null)
            eventPublisher.publish(MemberAccessed(100L, AccessType.WITHDRAW, "203.0.113.7"))
        }
        verify(exactly = 1) { snsTokenRevoker.revoke(any()) }
        assertEquals(listOf<Any>(MemberAccessed(100L, AccessType.WITHDRAW, "203.0.113.7")), events)
    }

    /** 회원과 접속지는 인자로 받는다 — 접속지를 알 수 없으면 null 그대로 남긴다. */
    @Test
    fun `접속지를 알 수 없는 탈퇴는 접속기록에 null을 남긴다`() {
        givenTarget()

        service.withdraw(100L, null)

        assertEquals(listOf<Any>(MemberAccessed(100L, AccessType.WITHDRAW, null)), events)
    }

    /** 카카오는 어드민 키로 해제하므로 저장된 토큰이 없다. */
    @Test
    fun `저장된 토큰이 없는 회원은 토큰 없이 식별자만으로 해제를 요청한다`() {
        givenTarget(provider = Provider.KAKAO, providerUserId = "kakao-9911", token = null)

        service.withdraw(100L, "203.0.113.7")

        verify { snsTokenRevoker.revoke(RevokeSnsConnectionCommand(Provider.KAKAO, "kakao-9911", null)) }
        verify { members.erase(100L, Provider.KAKAO, unlinkFailureReason = null) }
    }

    /** R11. 해제 실패는 탈퇴를 막지 않고, 사유만 삭제 단계로 넘긴다. */
    @Test
    fun `provider 해제가 실패해도 탈퇴는 끝까지 가고 실패 사유를 삭제에 넘긴다`() {
        givenTarget(revokeResult = SnsRevokeResult.Failed("HTTP 400 invalid_grant"))

        val result = service.withdraw(100L, "203.0.113.7")

        assertTrue(result.success)
        verifyOrder {
            sessions.revokeAll(100L)
            members.erase(100L, Provider.APPLE, unlinkFailureReason = "HTTP 400 invalid_grant")
        }
        assertEquals(listOf<Any>(MemberAccessed(100L, AccessType.WITHDRAW, "203.0.113.7")), events)
    }

    /** 해제 대상이 아닌 회원(EMAIL, 토큰 없는 회원)은 실패가 아니다. */
    @Test
    fun `해제를 건너뛴 회원은 실패 사유 없이 삭제한다`() {
        givenTarget(provider = Provider.EMAIL, providerUserId = "protected-identifier", token = null, revokeResult = SnsRevokeResult.Skipped)

        val result = service.withdraw(100L, "203.0.113.7")

        assertTrue(result.success)
        verify(exactly = 1) { members.erase(100L, Provider.EMAIL, unlinkFailureReason = null) }
    }

    /** 세션이 살아 있는 채로 회원만 지워지면 안 된다. */
    @Test
    fun `세션 폐기가 실패하면 삭제하지 않고 접속기록도 남기지 않는다`() {
        givenTarget()
        every { sessions.revokeAll(100L) } throws IllegalStateException("세션 저장소 장애")

        assertFailsWith<IllegalStateException> { service.withdraw(100L, "203.0.113.7") }

        verify(exactly = 0) { members.erase(any(), any(), any()) }
        assertTrue(events.isEmpty())
    }

    @Test
    fun `탈퇴 대상 회원이 없으면 해제도 세션 폐기도 삭제도 하지 않는다`() {
        every { members.withdrawalTarget(100L) } throws MemberNotFoundException("회원을 찾을 수 없습니다.")

        val ex = assertFailsWith<MemberNotFoundException> { service.withdraw(100L, "203.0.113.7") }

        assertEquals("회원을 찾을 수 없습니다.", ex.message)
        verify(exactly = 0) { snsTokenRevoker.revoke(any()) }
        verify(exactly = 0) { sessions.revokeAll(any()) }
        verify(exactly = 0) { members.erase(any(), any(), any()) }
        assertTrue(events.isEmpty())
    }

    @Test
    fun `삭제가 실패하면 접속기록을 남기지 않는다`() {
        givenTarget()
        every { members.erase(any(), any(), any()) } throws IllegalStateException("삭제 실패")

        assertFailsWith<IllegalStateException> { service.withdraw(100L, "203.0.113.7") }

        assertTrue(events.isEmpty())
    }
}
