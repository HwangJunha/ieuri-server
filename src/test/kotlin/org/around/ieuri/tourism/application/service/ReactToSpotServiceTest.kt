package org.around.ieuri.tourism.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.tourism.application.command.ReactToSpotCommand
import org.around.ieuri.tourism.application.exception.SpotNotFoundException
import org.around.ieuri.tourism.application.port.out.persistence.SpotReactionPort
import org.around.ieuri.tourism.domain.aggregate.SpotReaction
import org.around.ieuri.tourism.domain.vo.ReactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * ReactToSpotService — 카탈로그 기반 저장과 재평가 덮어쓰기(AC-13).
 *
 * **식별 단위는 `(memberId, catalogId)`다**(SPEC-0019 R-10) — 클라이언트가 보내는 `spot.id`는
 * 언어별로 갈리므로, 서비스가 카탈로그로 해석한 뒤에야 같은 장소의 좋아요가 한 기록으로 모인다.
 * `spotId`는 어느 언어 화면에서 눌렀는지의 기록으로만 남는다.
 */
class ReactToSpotServiceTest {

    private val port = mockk<SpotReactionPort>(relaxed = true)
    private val service = ReactToSpotService(port)

    @Test
    fun `신규 기록은 해석된 catalogId로 저장되고 spotId는 출처로 남는다`() {
        every { port.findCatalogIdBySpotId(12345L) } returns 777L
        every { port.find(1L, 777L) } returns null
        val saved = slot<SpotReaction>()
        every { port.save(capture(saved)) } returns Unit

        service.react(ReactToSpotCommand(12345L, ReactionType.LIKE), memberId = 1L)

        assertEquals(1L, saved.captured.memberId)
        assertEquals(777L, saved.captured.catalogId)
        assertEquals(12345L, saved.captured.spotId)
        assertEquals(ReactionType.LIKE, saved.captured.reaction)
    }

    @Test
    fun `같은 장소의 다른 언어 spot은 같은 조회 키가 된다`() {
        // 언어가 다르면 spot 행이 갈리지만 카탈로그는 하나다 — 좋아요가 언어별로 갈리지 않는다.
        every { port.findCatalogIdBySpotId(999L) } returns 777L
        every { port.find(1L, 777L) } returns null

        service.react(ReactToSpotCommand(999L, ReactionType.LIKE), memberId = 1L)

        verify { port.find(1L, 777L) }
    }

    @Test
    fun `카탈로그가 없는 spot은 SPOT_NOT_FOUND다`() {
        // 아직 배정되지 않았거나 사라진 행이며, 어느 쪽이든 좋아요를 붙일 장소가 없다.
        every { port.findCatalogIdBySpotId(12345L) } returns null

        assertFailsWith<SpotNotFoundException> {
            service.react(ReactToSpotCommand(12345L, ReactionType.LIKE), memberId = 1L)
        }
        verify(exactly = 0) { port.save(any()) }
    }

    @Test
    fun `재평가는 기존 기록을 덮어쓴다`() {
        val existing = SpotReaction.record(1L, 777L, 12345L, ReactionType.LIKE)
        every { port.findCatalogIdBySpotId(12345L) } returns 777L
        every { port.find(1L, 777L) } returns existing

        service.react(ReactToSpotCommand(12345L, ReactionType.DISLIKE), memberId = 1L)

        assertEquals(ReactionType.DISLIKE, existing.reaction)
        verify { port.save(existing) }
    }

    @Test
    fun `유효하지 않은 spotId 커맨드는 생성 시점에 거부된다`() {
        // 문자열 blank 검사가 양수 검사로 바뀌었다 — 식별자가 Long이 되면서
        // "비어 있음"이라는 상태 자체가 사라졌다.
        assertFailsWith<IllegalArgumentException> { ReactToSpotCommand(0L, ReactionType.LIKE) }
        assertFailsWith<IllegalArgumentException> { ReactToSpotCommand(-1L, ReactionType.LIKE) }
    }
}
