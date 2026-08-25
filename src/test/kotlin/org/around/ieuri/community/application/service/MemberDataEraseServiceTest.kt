package org.around.ieuri.community.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.around.ieuri.community.application.port.out.persistence.MemberContentSoftDeletePort
import org.around.ieuri.community.application.port.out.persistence.MemberDataErasePort
import org.around.ieuri.community.application.port.out.persistence.SpotQuestionCounterPort
import org.around.ieuri.community.application.port.out.client.ClockPort
import java.time.Instant

import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * 탈퇴 회원의 커뮤니티 데이터 정리(SPEC-0011 R17).
 *
 * 고정하는 계약은 둘이다.
 * - **콘텐츠는 소프트 삭제한다** — 글·댓글·질문·답변·답글은 남에게도 보이므로 지우면 남의
 *   스레드에 구멍이 난다. 행은 남기고 표시에서만 지운다.
 * - **본인만의 데이터는 물리 삭제한다** — 좋아요·숨김·차단·DM은 남길 이유가 없다.
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

    /**
     * **행을 지우지 않는다.** 물리 삭제하면 그 글에 달린 남의 댓글·좋아요가 CASCADE로 함께
     * 사라지고, 질문을 지우면 남이 쓴 답변이 통째로 없어진다 — 이 슬라이스가 고친 것이다.
     */
    @Test
    fun `콘텐츠를 물리 삭제하지 않는다`() {
        service.eraseMemberData(memberId)

        // 콘텐츠 삭제 메서드는 포트에서 사라졌고, 남은 물리 삭제는 본인 데이터뿐이다.
        verify(exactly = 1) { erasePort.deleteLikesByMember(memberId) }
        verify(exactly = 1) { erasePort.deleteHidesByMember(memberId) }
        verify(exactly = 1) { erasePort.deleteBlocksInvolving(memberId) }
    }

    /** 한 번의 탈퇴가 남기는 시각은 하나여야 한다 — 표마다 갈리면 한 값으로 읽을 수 없다. */
    @Test
    fun `모든 콘텐츠가 같은 삭제 시각을 받는다`() {
        service.eraseMemberData(memberId)

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

    /**
     * SPEC-0017 AC17-1 — 탈퇴하면 그가 보낸 DM 메시지도 지워진다.
     *
     * **대화 자체는 지우지 않는다**(AC17-2) — 상대방 화면에서 남은 자기 메시지가 보여야 하고
     * 조회가 실패하면 안 된다.
     */
    @Test
    fun `탈퇴하면 보낸 메시지도 지운다`() {
        service.eraseMemberData(memberId)

        verify { erasePort.deleteMessagesBySender(memberId) }
    }

    /**
     * AC17-3 — 메시지 삭제는 **탈퇴 경로에서만** 일어난다. 정지는 해제될 수 있어 되돌릴 수
     * 없는 삭제를 하지 않으며, 이 유스케이스는 `MemberWithdrawn`에만 연결되어 있다.
     */
    @Test
    fun `메시지 삭제는 한 번만 일어난다`() {
        service.eraseMemberData(memberId)

        verify(exactly = 1) { erasePort.deleteMessagesBySender(memberId) }
    }

    // ── answerCount ──

    /**
     * 답변을 지운 만큼 남는 질문의 `answerCount`를 내린다(SPEC-0026 R17).
     *
     * **세는 것이 칠하는 것보다 먼저다** — 칠하고 나면 어느 질문의 수를 내려야 하는지 알 수 없다.
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

        // 같은 질문에 두 건이면 두 번 내린다 — 중복을 접으면 수가 실제보다 크게 남는다.
        verify(exactly = 2) { counterPort.decreaseAnswerCount(11L) }
        verify(exactly = 1) { counterPort.decreaseAnswerCount(22L) }
    }

    /** 대상이 없으면 감소도 없다 — 숨겨진 답변은 애초에 수에 들어 있지 않다. */
    @Test
    fun `내릴 질문이 없으면 답변 수를 건드리지 않는다`() {
        every { softDeletePort.findQuestionIdsToDecrease(memberId) } returns emptyList()

        service.eraseMemberData(memberId)

        verify(exactly = 0) { counterPort.decreaseAnswerCount(any()) }
    }

    /** 칠하기가 실패하면 수를 내리지 않는다 — 먼저 내리면 수만 줄어든 상태가 남는다. */
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
