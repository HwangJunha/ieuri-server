package org.around.ieuri.tourism.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.tourism.application.command.ReactToSpotCommand
import org.around.ieuri.tourism.application.port.out.persistence.SpotReactionPort
import org.around.ieuri.tourism.domain.aggregate.SpotReaction
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.ReactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** ReactToSpotService — 언어 결합 저장(AC-12)과 재평가 덮어쓰기(AC-13). */
class ReactToSpotServiceTest {

    private val port = mockk<SpotReactionPort>(relaxed = true)
    private val service = ReactToSpotService(port)

    @Test
    fun `신규 기록은 요청 언어와 결합해 저장된다`() {
        every { port.find(1L, "12345", DomainLocale.KO) } returns null
        val saved = slot<SpotReaction>()
        every { port.save(capture(saved)) } returns Unit

        service.react(ReactToSpotCommand("12345", ReactionType.LIKE), memberId = 1L, locale = DomainLocale.KO)

        assertEquals(1L, saved.captured.memberId)
        assertEquals("12345", saved.captured.contentId)
        assertEquals(DomainLocale.KO, saved.captured.language)
        assertEquals(ReactionType.LIKE, saved.captured.reaction)
    }

    @Test
    fun `같은 contentId라도 언어가 다르면 별도 조회 키로 저장한다`() {
        every { port.find(1L, "12345", DomainLocale.EN) } returns null
        service.react(ReactToSpotCommand("12345", ReactionType.LIKE), memberId = 1L, locale = DomainLocale.EN)
        verify { port.find(1L, "12345", DomainLocale.EN) }
    }

    @Test
    fun `재평가는 기존 기록을 덮어쓴다`() {
        val existing = SpotReaction.record(1L, "12345", DomainLocale.KO, ReactionType.LIKE)
        every { port.find(1L, "12345", DomainLocale.KO) } returns existing

        service.react(ReactToSpotCommand("12345", ReactionType.DISLIKE), memberId = 1L, locale = DomainLocale.KO)

        assertEquals(ReactionType.DISLIKE, existing.reaction)
        verify { port.save(existing) }
    }

    @Test
    fun `빈 spotId 커맨드는 생성 시점에 거부된다`() {
        // spotId blank는 커맨드 init에서 걸러진다(서비스의 SpotNotFound 방어는 이중 안전망).
        assertFailsWith<IllegalArgumentException> { ReactToSpotCommand("   ", ReactionType.LIKE) }
    }
}
