package org.around.ieuri.community.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.around.ieuri.community.application.port.out.client.ClockPort
import org.around.ieuri.community.application.port.out.persistence.MemberContentSoftDeletePort
import org.around.ieuri.community.application.port.out.persistence.MemberDataErasePort
import org.around.ieuri.community.application.port.out.persistence.SpotQuestionCounterPort
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * 탈퇴 회원의 커뮤니티 데이터 정리(SPEC-0011 R17).
 * - 남에게도 보이는 콘텐츠(글·댓글·질문·답변·답글·DM)는 소프트 삭제한다.
 * - 본인만의 데이터(좋아요·숨김·차단)는 물리 삭제한다.
 */
class MemberDataEraseServiceTest {

    private val memberId = 7L
    private val now = Instant.parse("2026-08-25T00:00:00Z")

    private val erasePort = mockk<MemberDataErasePort>(relaxed = true)
    private val softDeletePort = mockk<MemberContentSoftDeletePort>(relaxed = true)
    private val counterPort = mockk<SpotQuestionCounterPort>(relaxed = true)
    private val service = MemberDataEraseService(
        erasePort,
        softDeletePort,
        counterPort,
        mockk<ClockPort> { every { now() } returns now },
    )

    // ── 콘텐츠: 소프트 삭제 ──

    @Test
    fun `글과 댓글을 소프트 삭제한다`() {
        service.eraseMemberData(memberId)

        verify { softDeletePort.softDeletePostsByAuthor(memberId, now) }
        verify { softDeletePort.softDeleteCommentsByAuthor(memberId, now) }
    }

    @Test
    fun `장소 질문 답변 답글을 소프트 삭제한다`() {
        service.eraseMemberData(memberId)

        verify { softDeletePort.softDeleteSpotQuestionsByAuthor(memberId, now) }
        verify { softDeletePort.softDeleteSpotAnswersByAuthor(memberId, now) }
        verify { softDeletePort.softDeleteSpotAnswerRepliesByAuthor(memberId, now) }
    }

    /** 콘텐츠 행은 물리 삭제하지 않는다. */
    @Test
    fun `콘텐츠를 물리 삭제하지 않는다`() {
        service.eraseMemberData(memberId)

        // 물리 삭제는 본인 데이터뿐이다.
        verify(exactly = 1) { erasePort.deleteLikesByMember(memberId) }
        verify(exactly = 1) { erasePort.deleteHidesByMember(memberId) }
        verify(exactly = 1) { erasePort.deleteBlocksInvolving(memberId) }
    }

    /** 한 번의 탈퇴는 모든 표에 같은 시각을 남긴다. */
    @Test
    fun `모든 콘텐츠가 같은 삭제 시각을 받는다`() {
        service.eraseMemberData(memberId)

        verify { softDeletePort.softDeleteMessagesBySender(memberId, now) }
        verify { softDeletePort.softDeletePostsByAuthor(memberId, now) }
        verify { softDeletePort.softDeleteCommentsByAuthor(memberId, now) }
        verify { softDeletePort.softDeleteSpotQuestionsByAuthor(memberId, now) }
        verify { softDeletePort.softDeleteSpotAnswersByAuthor(memberId, now) }
        verify { softDeletePort.softDeleteSpotAnswerRepliesByAuthor(memberId, now) }
    }

    // ── 본인만의 데이터: 물리 삭제 ──

    @Test
    fun `좋아요 숨김 차단을 지운다`() {
        service.eraseMemberData(memberId)

        verify { erasePort.deleteLikesByMember(memberId) }
        verify { erasePort.deleteHidesByMember(memberId) }
        verify { erasePort.deleteBlocksInvolving(memberId) }
    }

    /** SPEC-0017 AC17-1·AC17-2 — 탈퇴자가 보낸 DM은 상대 화면에서 사라지되 행은 남는다. */
    @Test
    fun `탈퇴하면 보낸 메시지를 소프트 삭제한다`() {
        service.eraseMemberData(memberId)

        verify { softDeletePort.softDeleteMessagesBySender(memberId, now) }
    }

    /** AC17-3 — 메시지 처리는 탈퇴(`MemberWithdrawn`) 경로에서만 일어난다. */
    @Test
    fun `메시지 소프트 삭제는 한 번만 일어난다`() {
        service.eraseMemberData(memberId)

        verify(exactly = 1) { softDeletePort.softDeleteMessagesBySender(memberId, now) }
    }

    // ── answerCount ──

    /**
     * 지운 답변 수만큼 질문의 `answerCount`를 내린다(SPEC-0026 R17).
     * - 세는 것이 삭제 표시보다 먼저다.
     */
    @Test
    fun `답변을 칠하기 전에 대상 질문을 먼저 센다`() {
        every { softDeletePort.findQuestionIdsToDecrease(memberId) } returns listOf(11L)

        service.eraseMemberData(memberId)

        verifyOrder {
            softDeletePort.findQuestionIdsToDecrease(memberId)
            softDeletePort.softDeleteSpotAnswersByAuthor(memberId, now)
        }
    }

    @Test
    fun `지운 답변이 달렸던 질문의 답변 수를 내린다`() {
        every { softDeletePort.findQuestionIdsToDecrease(memberId) } returns listOf(11L, 11L, 22L)

        service.eraseMemberData(memberId)

        // 같은 질문에 두 건이면 두 번 내린다.
        verify(exactly = 2) { counterPort.decreaseAnswerCount(11L) }
        verify(exactly = 1) { counterPort.decreaseAnswerCount(22L) }
    }

    /** 대상이 없으면 감소도 없다. */
    @Test
    fun `내릴 질문이 없으면 답변 수를 건드리지 않는다`() {
        every { softDeletePort.findQuestionIdsToDecrease(memberId) } returns emptyList()

        service.eraseMemberData(memberId)

        verify(exactly = 0) { counterPort.decreaseAnswerCount(any()) }
    }

    /** 삭제 표시가 실패하면 수를 내리지 않는다. */
    @Test
    fun `답변 소프트 삭제가 실패하면 답변 수를 내리지 않는다`() {
        every { softDeletePort.findQuestionIdsToDecrease(memberId) } returns listOf(11L)
        every { softDeletePort.softDeleteSpotAnswersByAuthor(memberId, now) } throws
            RuntimeException("db down")

        assertFailsWith<RuntimeException> { service.eraseMemberData(memberId) }

        verify(exactly = 0) { counterPort.decreaseAnswerCount(any()) }
    }

    // ── 공통 ──

    @Test
    fun `정리할 것이 없어도 오류가 아니다`() {
        every { softDeletePort.findQuestionIdsToDecrease(memberId) } returns emptyList()
        every { softDeletePort.softDeletePostsByAuthor(memberId, now) } returns 0
        every { softDeletePort.softDeleteCommentsByAuthor(memberId, now) } returns 0
        every { erasePort.deleteLikesByMember(memberId) } returns 0
        every { erasePort.deleteHidesByMember(memberId) } returns 0
        every { erasePort.deleteBlocksInvolving(memberId) } returns 0

        service.eraseMemberData(memberId)
    }

    @Test
    fun `저장소 실패는 호출자에게 올라간다`() {
        // 삼키는 것은 리스너의 책임이다 — 서비스는 실패를 감추지 않는다.
        every { softDeletePort.softDeletePostsByAuthor(memberId, now) } throws RuntimeException("db down")

        assertFailsWith<RuntimeException> { service.eraseMemberData(memberId) }
    }
}
