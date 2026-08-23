package org.around.ieuri.tourism.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.tourism.application.command.ReactToSpotCommand
import org.around.ieuri.tourism.application.exception.SpotNotFoundException
import org.around.ieuri.tourism.application.exception.SpotNotFoundInLocaleException
import org.around.ieuri.tourism.application.port.out.persistence.SpotReactionPort
import org.around.ieuri.tourism.application.port.out.query.SpotBasics
import org.around.ieuri.tourism.application.port.out.query.SpotQueryPort
import org.around.ieuri.tourism.domain.aggregate.SpotReaction
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.ReactionType
import org.around.ieuri.tourism.domain.vo.SpotCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * ReactToSpotService — 언어별 저장과 재평가 덮어쓰기(SPEC-0029 R-13).
 *
 * **식별 단위는 `(memberId, catalogId, language)`다.** 클라이언트는 카탈로그 id만 보내고
 * 언어는 서버가 활성 언어로 정한다. 같은 장소라도 언어마다 별도의 기록이며, 재평가는 같은
 * 언어의 기록만 덮어쓴다.
 */
class ReactToSpotServiceTest {

    private val port = mockk<SpotReactionPort>(relaxed = true)
    private val queryPort = mockk<SpotQueryPort>(relaxed = true)
    private val service = ReactToSpotService(port, queryPort)

    private val basics = SpotBasics(
        title = "경복궁",
        address = "서울 종로구",
        thumbnailImageUrl = "https://img/1.jpg",
        category = SpotCategory("TOURIST_SPOT"),
        copyright = CopyrightType.TYPE1,
        longitude = 126.9,
        latitude = 37.5,
    )

    private fun resolvedIn(language: DomainLocale, catalogId: Long = 777L) {
        every { queryPort.findSpotBasics(catalogId, language) } returns basics
    }

    @Test
    fun `신규 기록은 커맨드의 catalogId와 활성 언어로 저장된다`() {
        resolvedIn(DomainLocale.JA)
        every { port.find(1L, 777L, DomainLocale.JA) } returns null
        val saved = slot<SpotReaction>()
        every { port.save(capture(saved)) } returns Unit

        service.react(ReactToSpotCommand(777L, ReactionType.LIKE), memberId = 1L, language = DomainLocale.JA)

        assertEquals(1L, saved.captured.memberId)
        assertEquals(777L, saved.captured.catalogId)
        assertEquals(DomainLocale.JA, saved.captured.language)
        assertEquals(ReactionType.LIKE, saved.captured.reaction)
    }

    @Test
    fun `같은 카탈로그라도 언어가 다르면 다른 조회 키가 된다`() {
        // 영어로 누른 기록은 일본어 조회에 걸리지 않는다 — 그것이 언어별 축의 정의다(AC-13.2).
        resolvedIn(DomainLocale.EN)
        every { port.find(1L, 777L, DomainLocale.EN) } returns null

        service.react(ReactToSpotCommand(777L, ReactionType.LIKE), memberId = 1L, language = DomainLocale.EN)

        verify { port.find(1L, 777L, DomainLocale.EN) }
        verify(exactly = 0) { port.find(1L, 777L, DomainLocale.JA) }
    }

    @Test
    fun `없는 카탈로그는 SPOT_NOT_FOUND다`() {
        // FK 위반으로 터뜨리면 클라이언트가 계약에 없는 오류를 받는다.
        every { queryPort.findSpotBasics(12345L, DomainLocale.EN) } returns null
        every { port.existsCatalog(12345L) } returns false

        assertFailsWith<SpotNotFoundException> {
            service.react(ReactToSpotCommand(12345L, ReactionType.LIKE), memberId = 1L, language = DomainLocale.EN)
        }
        verify(exactly = 0) { port.save(any()) }
    }

    @Test
    fun `카탈로그는 있으나 활성 언어에 원본이 없으면 SPOT_NOT_FOUND_IN_LOCALE이다`() {
        // 그 언어에서 볼 수 없는 장소에는 좋아요를 남길 수 없다(AC-13.4).
        every { queryPort.findSpotBasics(777L, DomainLocale.ZH) } returns null
        every { port.existsCatalog(777L) } returns true

        assertFailsWith<SpotNotFoundInLocaleException> {
            service.react(ReactToSpotCommand(777L, ReactionType.LIKE), memberId = 1L, language = DomainLocale.ZH)
        }
        verify(exactly = 0) { port.save(any()) }
    }

    @Test
    fun `재평가는 같은 언어의 기존 기록을 덮어쓴다`() {
        val existing = SpotReaction.record(1L, 777L, DomainLocale.JA, ReactionType.LIKE)
        resolvedIn(DomainLocale.JA)
        every { port.find(1L, 777L, DomainLocale.JA) } returns existing

        service.react(ReactToSpotCommand(777L, ReactionType.DISLIKE), memberId = 1L, language = DomainLocale.JA)

        assertEquals(ReactionType.DISLIKE, existing.reaction)
        assertEquals(DomainLocale.JA, existing.language)
        verify { port.save(existing) }
    }

    @Test
    fun `다른 언어에 기록이 있어도 이 언어에서는 새 기록이 생긴다`() {
        // 일본어 기록이 있다는 사실이 영어의 저장 경로에 영향을 주면 안 된다.
        resolvedIn(DomainLocale.EN)
        every { port.find(1L, 777L, DomainLocale.EN) } returns null
        val saved = slot<SpotReaction>()
        every { port.save(capture(saved)) } returns Unit

        service.react(ReactToSpotCommand(777L, ReactionType.DISLIKE), memberId = 1L, language = DomainLocale.EN)

        assertEquals(DomainLocale.EN, saved.captured.language)
        assertEquals(ReactionType.DISLIKE, saved.captured.reaction)
    }

    @Test
    fun `유효하지 않은 catalogId 커맨드는 생성 시점에 거부된다`() {
        // 문자열 blank 검사가 양수 검사로 바뀌었다 — 식별자가 Long이 되면서
        // "비어 있음"이라는 상태 자체가 사라졌다.
        assertFailsWith<IllegalArgumentException> { ReactToSpotCommand(0L, ReactionType.LIKE) }
        assertFailsWith<IllegalArgumentException> { ReactToSpotCommand(-1L, ReactionType.LIKE) }
    }
}
