package org.around.ieuri.tourism.application.sharelink

import io.mockk.every
import io.mockk.mockk
import org.around.ieuri.tourism.application.shared.NationwideMapPolicy
import org.around.ieuri.tourism.application.sharelink.required.MapShareLinkRecord
import org.around.ieuri.tourism.application.sharelink.required.MapShareLinkRepository
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MapShareLinkModifyServiceTest {

    private val repository = mockk<MapShareLinkRepository>()
    private val service = MapShareLinkModifyService(
        repository,
        NationwideMapPolicy(maxCellSpotPageSize = 50, shareUrlBase = "https://www.ieuri.co.kr/tour/korea-map", maxRelationPageSize = 50),
    )

    @Test
    fun `공유 링크는 발급을 반복해도 같은 URL이다`() {
        val existing = MapShareLinkRecord(7L, "already-issued", Instant.parse("2026-08-01T00:00:00Z"))
        // 새 토큰을 만들어 넘겨도 저장소가 기존 행을 돌려준다.
        every { repository.findOrCreate(eq(7L), any()) } returns existing

        val expected = "https://www.ieuri.co.kr/tour/korea-map/already-issued"
        assertEquals(expected, service.createShareLink(7L).shareUrl)
        assertEquals(expected, service.createShareLink(7L).shareUrl)
        assertEquals(existing.createdAt, service.createShareLink(7L).createdAt)
    }

    @Test
    fun `폐기는 URL에서 토큰을 꺼내 넘긴다`() {
        every { repository.revoke(7L, "kJ3xQ9") } returns true

        assertTrue(service.revokeShareLink(7L, "https://www.ieuri.co.kr/tour/korea-map/kJ3xQ9"))

        // 도메인·경로가 바뀌어도 마지막 조각이 토큰이라 그대로 동작한다.
        assertTrue(service.revokeShareLink(7L, "https://ieuri.co.kr/shared/kJ3xQ9"))
    }

    @Test
    fun `남의 링크나 이미 폐기된 링크는 false다`() {
        every { repository.revoke(7L, any()) } returns false

        assertFalse(service.revokeShareLink(7L, "https://www.ieuri.co.kr/tour/korea-map/other"))
    }

    @Test
    fun `탈퇴 정리는 링크를 폐기가 아니라 삭제하고 건수를 돌려준다`() {
        every { repository.deleteAll(7L) } returns 1

        assertEquals(1, service.eraseMemberContent(7L))
    }
}
