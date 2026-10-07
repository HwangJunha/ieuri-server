package org.around.ieuri.tourism.application.relation

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.tourism.application.footprint.required.FootprintQueryPort
import org.around.ieuri.tourism.application.relation.required.MapRelationRepository
import org.around.ieuri.tourism.application.shared.provided.exception.ShareLinkBlockedException
import org.around.ieuri.tourism.application.shared.provided.exception.ShareLinkNotFoundException
import org.around.ieuri.tourism.application.shared.required.MapViewerBlockPort
import org.around.ieuri.tourism.domain.relation.CannotRelateSelfException
import org.around.ieuri.tourism.domain.relation.MapRelation
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MapRelationModifyServiceTest {

    private val relationRepository = mockk<MapRelationRepository>(relaxed = true)
    private val footprintQueryPort = mockk<FootprintQueryPort>()
    private val viewerBlockPort = mockk<MapViewerBlockPort>()

    private val service = MapRelationModifyService(relationRepository, footprintQueryPort, viewerBlockPort)

    @Test
    fun `공유 링크로 관계를 맺으면 상대 id를 돌려주고 나에서 상대 방향으로 저장한다`() {
        givenActiveLink(ownerId = 20L)
        every { viewerBlockPort.isBlockedBetween(10L, 20L) } returns false

        val counterpartId = service.createRelation(memberId = 10L, shareUrl = SHARE_URL)

        assertEquals(20L, counterpartId)
        verify(exactly = 1) { relationRepository.relate(MapRelation(ownerId = 10L, counterpartId = 20L)) }
    }

    @Test
    fun `링크는 URL의 마지막 조각인 토큰으로 찾는다`() {
        givenActiveLink(ownerId = 20L)
        every { viewerBlockPort.isBlockedBetween(10L, 20L) } returns false

        service.createRelation(memberId = 10L, shareUrl = SHARE_URL)

        verify(exactly = 1) { footprintQueryPort.findSharerId(TOKEN) }
    }

    @Test
    fun `폐기됐거나 없는 링크면 SHARE_LINK_NOT_FOUND다`() {
        every { footprintQueryPort.findSharerId(any()) } returns null

        assertThrows<ShareLinkNotFoundException> { service.createRelation(memberId = 10L, shareUrl = SHARE_URL) }
        verify(exactly = 0) { relationRepository.relate(any()) }
    }

    @Test
    fun `자기 링크로는 관계를 맺을 수 없고 차단 여부는 묻지 않는다`() {
        givenActiveLink(ownerId = 10L)

        assertThrows<CannotRelateSelfException> { service.createRelation(memberId = 10L, shareUrl = SHARE_URL) }
        verify(exactly = 0) { relationRepository.relate(any()) }
        verify(exactly = 0) { viewerBlockPort.isBlockedBetween(any(), any()) }
    }

    @Test
    fun `차단 관계면 등록 시점에 막힌다`() {
        givenActiveLink(ownerId = 20L)
        every { viewerBlockPort.isBlockedBetween(10L, 20L) } returns true

        assertThrows<ShareLinkBlockedException> { service.createRelation(memberId = 10L, shareUrl = SHARE_URL) }
        verify(exactly = 0) { relationRepository.relate(any()) }
    }

    @Test
    fun `삭제는 내 목록에서만 지운다`() {
        every { relationRepository.removeFromMyList(10L, 20L) } returns true

        assertTrue(service.removeRelation(memberId = 10L, counterpartId = 20L))
        verify(exactly = 1) { relationRepository.removeFromMyList(10L, 20L) }
    }

    @Test
    fun `없던 관계의 삭제는 false지만 오류가 아니다`() {
        every { relationRepository.removeFromMyList(10L, 99L) } returns false

        assertFalse(service.removeRelation(memberId = 10L, counterpartId = 99L))
    }

    @Test
    fun `탈퇴 정리는 양쪽 방향을 모두 지우고 건수를 돌려준다`() {
        every { relationRepository.deleteAllInvolving(10L) } returns 4

        assertEquals(4, service.eraseMemberContent(10L))
    }

    private fun givenActiveLink(ownerId: Long) {
        every { footprintQueryPort.findSharerId(any()) } returns ownerId
    }

    private companion object {
        const val TOKEN = "abcdefghijklmnopqrstuvwx"
        const val SHARE_URL = "https://www.ieuri.co.kr/tour/korea-map/$TOKEN"
    }
}
