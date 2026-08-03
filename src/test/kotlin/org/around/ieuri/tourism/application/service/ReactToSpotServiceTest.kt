package org.around.ieuri.tourism.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.tourism.application.command.ReactToSpotCommand
import org.around.ieuri.tourism.application.port.out.persistence.SpotReactionPort
import org.around.ieuri.tourism.domain.aggregate.SpotReaction
import org.around.ieuri.tourism.domain.vo.ReactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * ReactToSpotService — `spot.id` 기반 저장과 재평가 덮어쓰기(AC-13).
 *
 * **언어 결합(AC-12)이 사라졌다**(V20). 이전에는 `(memberId, contentId, language)`로 저장해야 했다 —
 * TourAPI contentId가 언어별로 갈렸기 때문이다. 이제 `spot.id`가 그 조합에 1:1로 대응하므로 언어를
 * 따로 들 이유가 없다. "같은 장소의 다른 언어"는 애초에 다른 `spotId`다.
 */
class ReactToSpotServiceTest {

    private val port = mockk<SpotReactionPort>(relaxed = true)
    private val service = ReactToSpotService(port)

    @Test
    fun `신규 기록은 spotId로 저장된다`() {
        every { port.find(1L, 12345L) } returns null
        val saved = slot<SpotReaction>()
        every { port.save(capture(saved)) } returns Unit

        service.react(ReactToSpotCommand(12345L, ReactionType.LIKE), memberId = 1L)

        assertEquals(1L, saved.captured.memberId)
        assertEquals(12345L, saved.captured.spotId)
        assertEquals(ReactionType.LIKE, saved.captured.reaction)
    }

    @Test
    fun `다른 spotId는 별도 조회 키다`() {
        // 언어가 다른 같은 장소는 다른 spot 행이므로 여기서 자연히 갈린다 —
        // 서비스가 언어를 알 필요가 없어졌다.
        every { port.find(1L, 999L) } returns null

        service.react(ReactToSpotCommand(999L, ReactionType.LIKE), memberId = 1L)

        verify { port.find(1L, 999L) }
    }

    @Test
    fun `재평가는 기존 기록을 덮어쓴다`() {
        val existing = SpotReaction.record(1L, 12345L, ReactionType.LIKE)
        every { port.find(1L, 12345L) } returns existing

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
