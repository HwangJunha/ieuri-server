package org.around.ieuri.tourism.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.tourism.application.port.out.persistence.SpotReactionPort
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** 탈퇴 회원의 좋아요/싫어요 삭제(SPEC-0011 R17). */
class MemberReactionEraseServiceTest {

    private val memberId = 7L
    private val reactionPort = mockk<SpotReactionPort>(relaxed = true)
    private val service = MemberReactionEraseService(reactionPort)

    @Test
    fun `회원의 좋아요 기록을 지운다`() {
        every { reactionPort.deleteByMember(memberId) } returns 3

        service.eraseMemberReactions(memberId)

        verify { reactionPort.deleteByMember(memberId) }
    }

    @Test
    fun `지울 것이 없어도 오류가 아니다`() {
        every { reactionPort.deleteByMember(memberId) } returns 0

        service.eraseMemberReactions(memberId)
    }

    @Test
    fun `저장소 실패는 호출자에게 올라간다`() {
        every { reactionPort.deleteByMember(memberId) } throws RuntimeException("db down")

        assertFailsWith<RuntimeException> { service.eraseMemberReactions(memberId) }
    }
}
