package org.around.ieuri.tourism.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.tourism.application.port.out.persistence.DistrictVisitStorePort
import org.around.ieuri.tourism.application.port.out.persistence.MapRelationPort
import org.around.ieuri.tourism.application.port.out.persistence.MapShareLinkPort
import org.around.ieuri.tourism.application.port.out.persistence.SpotReactionPort
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** 탈퇴 회원의 관광 데이터 삭제(SPEC-0011 R17, SPEC-0030 §9.1). */
class MemberReactionEraseServiceTest {

    private val memberId = 7L
    private val reactionPort = mockk<SpotReactionPort>(relaxed = true)
    private val visitPort = mockk<DistrictVisitStorePort>(relaxed = true)
    private val shareLinkPort = mockk<MapShareLinkPort>(relaxed = true)
    private val relationPort = mockk<MapRelationPort>(relaxed = true)
    private val service = MemberReactionEraseService(reactionPort, visitPort, shareLinkPort, relationPort)

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

    @Test
    fun `탈퇴하면 방문 기록과 공유 링크가 함께 사라진다`() {
        every { visitPort.removeAll(memberId) } returns 12
        every { shareLinkPort.deleteAll(memberId) } returns 1

        service.eraseMemberMapData(memberId)

        // 방문 기록은 보관 기간 없이 계정과 함께 사라진다(§9.1).
        verify { visitPort.removeAll(memberId) }
        verify { shareLinkPort.deleteAll(memberId) }
        // 관계는 양방향이라 상대 목록의 이 회원까지 지운다.
        verify { relationPort.deleteAllInvolving(memberId) }
    }

    @Test
    fun `지도 데이터가 없어도 오류가 아니다`() {
        every { visitPort.removeAll(memberId) } returns 0
        every { shareLinkPort.deleteAll(memberId) } returns 0

        service.eraseMemberMapData(memberId)
    }

    @Test
    fun `방문 기록 삭제가 실패하면 호출자에게 올라간다`() {
        every { visitPort.removeAll(memberId) } throws RuntimeException("db down")

        assertFailsWith<RuntimeException> { service.eraseMemberMapData(memberId) }
    }
}
