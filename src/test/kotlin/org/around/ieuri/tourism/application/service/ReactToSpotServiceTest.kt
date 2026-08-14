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
 * **식별 단위는 `(memberId, catalogId)`다**(SPEC-0019 R-10). 클라이언트가 카탈로그 id를 보내므로
 * 서비스가 다시 해석할 것은 없고, 어느 언어 화면에서 눌렀는지는 더 이상 기록에 남지 않는다 —
 * 언어가 식별에서 완전히 빠졌기 때문이다.
 */
class ReactToSpotServiceTest {

    private val port = mockk<SpotReactionPort>(relaxed = true)
    private val service = ReactToSpotService(port)

    @Test
    fun `신규 기록은 커맨드의 catalogId 그대로 저장된다`() {
        every { port.existsCatalog(777L) } returns true
        every { port.find(1L, 777L) } returns null
        val saved = slot<SpotReaction>()
        every { port.save(capture(saved)) } returns Unit

        service.react(ReactToSpotCommand(777L, ReactionType.LIKE), memberId = 1L)

        assertEquals(1L, saved.captured.memberId)
        assertEquals(777L, saved.captured.catalogId)
        assertEquals(ReactionType.LIKE, saved.captured.reaction)
    }

    @Test
    fun `같은 카탈로그는 어느 언어에서 눌러도 같은 조회 키가 된다`() {
        // 언어가 다르면 spot 행이 갈리지만 카탈로그는 하나다 — 좋아요가 언어별로 갈리지 않는다.
        every { port.existsCatalog(777L) } returns true
        every { port.find(1L, 777L) } returns null

        service.react(ReactToSpotCommand(777L, ReactionType.LIKE), memberId = 1L)

        verify { port.find(1L, 777L) }
    }

    @Test
    fun `없는 카탈로그는 SPOT_NOT_FOUND다`() {
        // FK 위반으로 터뜨리면 클라이언트가 계약에 없는 오류를 받는다.
        every { port.existsCatalog(12345L) } returns false

        assertFailsWith<SpotNotFoundException> {
            service.react(ReactToSpotCommand(12345L, ReactionType.LIKE), memberId = 1L)
        }
        verify(exactly = 0) { port.save(any()) }
    }

    @Test
    fun `재평가는 기존 기록을 덮어쓴다`() {
        val existing = SpotReaction.record(1L, 777L, ReactionType.LIKE)
        every { port.existsCatalog(777L) } returns true
        every { port.find(1L, 777L) } returns existing

        service.react(ReactToSpotCommand(777L, ReactionType.DISLIKE), memberId = 1L)

        assertEquals(ReactionType.DISLIKE, existing.reaction)
        verify { port.save(existing) }
    }

    @Test
    fun `유효하지 않은 catalogId 커맨드는 생성 시점에 거부된다`() {
        // 문자열 blank 검사가 양수 검사로 바뀌었다 — 식별자가 Long이 되면서
        // "비어 있음"이라는 상태 자체가 사라졌다.
        assertFailsWith<IllegalArgumentException> { ReactToSpotCommand(0L, ReactionType.LIKE) }
        assertFailsWith<IllegalArgumentException> { ReactToSpotCommand(-1L, ReactionType.LIKE) }
    }
}
