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
