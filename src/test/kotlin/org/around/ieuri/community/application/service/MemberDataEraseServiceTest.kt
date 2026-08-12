package org.around.ieuri.community.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.around.ieuri.community.application.port.out.persistence.MemberDataErasePort
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * 탈퇴 회원의 커뮤니티 데이터 삭제(SPEC-0011 R17).
 *
 * **글을 먼저 지운다** — 그래야 그 글에 달린 행이 cascade로 함께 사라지고, 남는 것이
 * 회원이 남의 글에 남긴 흔적이 된다.
 */
class MemberDataEraseServiceTest {

    private val memberId = 7L
    private val erasePort = mockk<MemberDataErasePort>(relaxed = true)
    private val service = MemberDataEraseService(erasePort)

    @Test
    fun `글 댓글 좋아요 숨김 차단을 모두 지운다`() {
        service.eraseMemberData(memberId)

        verify { erasePort.deletePostsByAuthor(memberId) }
        verify { erasePort.deleteCommentsByAuthor(memberId) }
        verify { erasePort.deleteLikesByMember(memberId) }
        verify { erasePort.deleteHidesByMember(memberId) }
        verify { erasePort.deleteBlocksInvolving(memberId) }
    }

    /**
     * SPEC-0017 AC17-1 — 탈퇴하면 피드와 그가 보낸 DM 메시지도 지워진다.
     *
     * **대화 자체는 지우지 않는다**(AC17-2) — 상대방 화면에서 남은 자기 메시지가 보여야 하고
     * 조회가 실패하면 안 된다.
     */
    @Test
    fun `탈퇴하면 피드와 보낸 메시지도 지운다`() {
        service.eraseMemberData(memberId)

        verify { erasePort.deleteFeedItemsByAuthor(memberId) }
        verify { erasePort.deleteMessagesBySender(memberId) }
    }

    /**
     * AC17-3 — 메시지 삭제는 **탈퇴 경로에서만** 일어난다. 정지는 해제될 수 있어 되돌릴 수
     * 없는 삭제를 하지 않으며, 이 유스케이스는 `MemberWithdrawn`에만 연결되어 있다
     * (CommunityMemberWithdrawnListener). 정지 이벤트에는 구독자가 없다.
     *
     * 이 유스케이스를 정지 경로에서도 부르게 되면 그 순간 R17이 깨지므로, 여기서는 "한 번의
     * 호출이 삭제만 하고 다른 상태를 건드리지 않는다"만 고정한다.
     */
    @Test
    fun `메시지 삭제는 한 번만 일어난다`() {
        service.eraseMemberData(memberId)

        // 정지 경로가 이 유스케이스를 재사용하면 같은 회원의 메시지가 다시 지워진다 —
        // 삭제는 탈퇴 1회로 끝나야 한다.
        verify(exactly = 1) { erasePort.deleteMessagesBySender(memberId) }
        verify(exactly = 1) { erasePort.deleteFeedItemsByAuthor(memberId) }
    }

    @Test
    fun `글을 댓글보다 먼저 지운다`() {
        service.eraseMemberData(memberId)

        verifyOrder {
            erasePort.deletePostsByAuthor(memberId)
            erasePort.deleteCommentsByAuthor(memberId)
        }
    }

    @Test
    fun `지울 것이 없어도 오류가 아니다`() {
        every { erasePort.deletePostsByAuthor(memberId) } returns 0
        every { erasePort.deleteCommentsByAuthor(memberId) } returns 0
        every { erasePort.deleteLikesByMember(memberId) } returns 0
        every { erasePort.deleteHidesByMember(memberId) } returns 0
        every { erasePort.deleteBlocksInvolving(memberId) } returns 0

        service.eraseMemberData(memberId)
    }

    @Test
    fun `저장소 실패는 호출자에게 올라간다`() {
        // 삼키는 것은 리스너의 책임이다 — 서비스는 실패를 감추지 않는다.
        every { erasePort.deletePostsByAuthor(memberId) } throws RuntimeException("db down")

        assertFailsWith<RuntimeException> { service.eraseMemberData(memberId) }
    }
}
