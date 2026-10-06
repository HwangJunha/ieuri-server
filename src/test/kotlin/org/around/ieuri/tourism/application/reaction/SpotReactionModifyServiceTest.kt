package org.around.ieuri.tourism.application.reaction

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.tourism.application.reaction.provided.dto.ReactToSpotCommand
import org.around.ieuri.tourism.application.reaction.provided.exception.SpotNotFoundException
import org.around.ieuri.tourism.application.reaction.provided.exception.SpotNotFoundInLocaleException
import org.around.ieuri.tourism.application.reaction.required.SpotReactionRepository
import org.around.ieuri.tourism.application.shared.required.SpotBasics
import org.around.ieuri.tourism.application.shared.required.SpotQueryPort
import org.around.ieuri.tourism.domain.reaction.ReactionType
import org.around.ieuri.tourism.domain.reaction.SpotReaction
import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.SpotCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SpotReactionModifyServiceTest {

    private val repository = mockk<SpotReactionRepository>(relaxed = true)
    private val queryPort = mockk<SpotQueryPort>(relaxed = true)
    private val service = SpotReactionModifyService(repository, queryPort)

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

    private fun savedReaction(): SpotReaction {
        val saved = slot<SpotReaction>()
        verify { repository.save(capture(saved)) }
        return saved.captured
    }

    @Test
    fun `신규 기록은 커맨드의 catalogId와 활성 언어로 저장된다`() {
        resolvedIn(DomainLocale.JA)

        service.react(ReactToSpotCommand(777L, ReactionType.LIKE), memberId = 1L, language = DomainLocale.JA)

        val saved = savedReaction()
        assertEquals(1L, saved.memberId)
        assertEquals(777L, saved.catalogId)
        assertEquals(DomainLocale.JA, saved.language)
        assertEquals(ReactionType.LIKE, saved.reaction)
    }

    @Test
    fun `없는 카탈로그는 SPOT_NOT_FOUND다`() {
        // FK 위반으로 터뜨리면 클라이언트가 계약에 없는 오류를 받는다.
        every { queryPort.findSpotBasics(12345L, DomainLocale.EN) } returns null
        every { queryPort.existsCatalog(12345L) } returns false

        assertFailsWith<SpotNotFoundException> {
            service.react(ReactToSpotCommand(12345L, ReactionType.LIKE), memberId = 1L, language = DomainLocale.EN)
        }
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `카탈로그는 있으나 활성 언어에 원본이 없으면 SPOT_NOT_FOUND_IN_LOCALE이다`() {
        // 그 언어에서 볼 수 없는 장소에는 좋아요를 남길 수 없다(AC-13.4).
        every { queryPort.findSpotBasics(777L, DomainLocale.ZH) } returns null
        every { queryPort.existsCatalog(777L) } returns true

        assertFailsWith<SpotNotFoundInLocaleException> {
            service.react(ReactToSpotCommand(777L, ReactionType.LIKE), memberId = 1L, language = DomainLocale.ZH)
        }
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `재평가는 기존 기록을 읽지 않고 새 반응으로 같은 키에 저장한다`() {
        resolvedIn(DomainLocale.JA)

        service.react(ReactToSpotCommand(777L, ReactionType.DISLIKE), memberId = 1L, language = DomainLocale.JA)

        val saved = savedReaction()
        assertEquals(ReactionType.DISLIKE, saved.reaction)
        assertEquals(DomainLocale.JA, saved.language)
        assertEquals(777L, saved.catalogId)
    }

    @Test
    fun `다른 언어에 기록이 있어도 이 언어에서는 새 기록이 생긴다`() {
        // 일본어 기록이 있다는 사실이 영어의 저장 경로에 영향을 주면 안 된다.
        resolvedIn(DomainLocale.EN)

        service.react(ReactToSpotCommand(777L, ReactionType.DISLIKE), memberId = 1L, language = DomainLocale.EN)

        val saved = savedReaction()
        assertEquals(DomainLocale.EN, saved.language)
        assertEquals(ReactionType.DISLIKE, saved.reaction)
    }

    @Test
    fun `유효하지 않은 catalogId 커맨드는 생성 시점에 거부된다`() {
        assertFailsWith<IllegalArgumentException> { ReactToSpotCommand(0L, ReactionType.LIKE) }
        assertFailsWith<IllegalArgumentException> { ReactToSpotCommand(-1L, ReactionType.LIKE) }
    }

    @Test
    fun `탈퇴 정리는 그 회원의 기록을 지우고 지운 건수를 돌려준다`() {
        every { repository.deleteByMember(1L) } returns 3

        assertEquals(3, service.eraseMemberContent(1L))
        verify(exactly = 1) { repository.deleteByMember(1L) }
    }
}
