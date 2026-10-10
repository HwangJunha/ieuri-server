package org.around.ieuri.tourism.application.catalog

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.tourism.application.catalog.provided.dto.ReactToSpotCommand
import org.around.ieuri.tourism.application.catalog.provided.exception.SpotNotFoundException
import org.around.ieuri.tourism.application.catalog.required.SpotCatalogRepository
import org.around.ieuri.tourism.domain.catalog.CatalogStandardValues
import org.around.ieuri.tourism.domain.catalog.CatalogTranslation
import org.around.ieuri.tourism.domain.catalog.ReactionType
import org.around.ieuri.tourism.domain.catalog.SpotCatalog
import org.around.ieuri.tourism.domain.catalog.SpotNotFoundInLocaleException
import org.around.ieuri.tourism.domain.catalog.SpotReaction
import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SpotCatalogModifyServiceTest {

    private val repository = mockk<SpotCatalogRepository>(relaxed = true)
    private val service = SpotCatalogModifyService(repository)

    private val catalog = SpotCatalog(
        id = 900,
        standard = CatalogStandardValues(129.1, 35.1, "https://img/1.jpg", SpotCategory.TOURIST_SPOT, CopyrightType.TYPE1),
        translations = listOf(CatalogTranslation(DomainLocale.KO, "마린시티", null, visible = true)),
    )

    @Test
    @DisplayName("반응은 카탈로그가 만들고 그대로 저장한다")
    fun `react saves the reaction made by the catalog`() {
        every { repository.findById(900) } returns catalog

        service.react(ReactToSpotCommand(900, ReactionType.LIKE), memberId = 7, language = DomainLocale.KO)

        verify { repository.saveReaction(SpotReaction(memberId = 7, catalogId = 900, language = DomainLocale.KO, reaction = ReactionType.LIKE)) }
    }

    @Test
    @DisplayName("없는 카탈로그는 SPOT_NOT_FOUND다")
    fun `missing catalog is not found`() {
        every { repository.findById(900) } returns null

        val error = assertThrows<SpotNotFoundException> { service.react(ReactToSpotCommand(900, ReactionType.LIKE), 7, DomainLocale.KO) }

        assertEquals("SPOT_NOT_FOUND", error.code)
        verify(exactly = 0) { repository.saveReaction(any()) }
    }

    @Test
    @DisplayName("이 언어에서 볼 수 없는 장소면 저장하지 않는다")
    fun `reaction in an unresolved language is not saved`() {
        every { repository.findById(900) } returns catalog

        val error = assertThrows<SpotNotFoundInLocaleException> { service.react(ReactToSpotCommand(900, ReactionType.LIKE), 7, DomainLocale.EN) }

        assertEquals("SPOT_NOT_FOUND_IN_LOCALE", error.code)
        verify(exactly = 0) { repository.saveReaction(any()) }
    }

    @Test
    @DisplayName("탈퇴 정리는 회원의 반응을 지운 수를 돌려준다")
    fun `erase deletes the member's reactions`() {
        every { repository.deleteReactionsByMember(7) } returns 4

        assertEquals(4, service.eraseMemberContent(7))
    }
}
