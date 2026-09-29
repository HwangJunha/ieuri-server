package org.around.ieuri.community.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.community.application.port.`in`.command.CatalogLanguage
import org.around.ieuri.community.application.port.out.persistence.CatalogLanguageEntry
import org.around.ieuri.community.application.port.out.persistence.PostSpotCleanupPort
import org.around.ieuri.community.application.port.out.persistence.SpotCatalogLanguagePort
import org.around.ieuri.community.domain.vo.ContentLanguage
import kotlin.test.Test
import kotlin.test.assertEquals

/** 사라진 장소를 가리키는 글-장소 연결 정리(SPEC-0023 R11, AC-8·AC-9). */
class PostSpotCleanupServiceTest {

    private val cleanupPort = mockk<PostSpotCleanupPort>()
    private val languagePort = mockk<SpotCatalogLanguagePort>(relaxed = true)
    private val service = PostSpotCleanupService(cleanupPort, languagePort)

    /** AC-8 — 삭제된 카탈로그를 가리키던 참조에만 정리가 1회 간다. */
    @Test
    fun `삭제된 카탈로그의 연결만 정리하고 정리는 한 번만 호출한다`() {
        val deleted = listOf(10L, 11L)
        every { cleanupPort.deleteByCatalogIds(deleted) } returns 3L

        service.cleanupDeletedCatalogs(deleted)

        // 넘긴 목록 그대로 한 번만 부른다.
        verify(exactly = 1) { cleanupPort.deleteByCatalogIds(deleted) }
    }

    /** 여러 카탈로그도 목록 그대로 한 번에 넘긴다. */
    @Test
    fun `여러 카탈로그를 한 번의 호출로 정리한다`() {
        val deleted = listOf(1L, 2L, 3L, 4L)
        every { cleanupPort.deleteByCatalogIds(deleted) } returns 9L

        service.cleanupDeletedCatalogs(deleted)

        verify(exactly = 1) { cleanupPort.deleteByCatalogIds(deleted) }
    }

    /** AC-9 — 정리 대상이 없으면 저장소를 건드리지 않고 예외도 내지 않는다. */
    @Test
    fun `정리 대상이 없으면 저장소를 호출하지 않는다`() {
        service.cleanupDeletedCatalogs(emptyList())

        verify(exactly = 0) { cleanupPort.deleteByCatalogIds(any()) }
    }

    /** AC-9 — 지워진 행이 0건이어도 정상이다. */
    @Test
    fun `지운 연결이 0건이어도 예외가 아니다`() {
        val deleted = listOf(42L)
        every { cleanupPort.deleteByCatalogIds(deleted) } returns 0L

        service.cleanupDeletedCatalogs(deleted)

        verify(exactly = 1) { cleanupPort.deleteByCatalogIds(deleted) }
    }

    // ── 카탈로그-언어 사본 동기화(SPEC-0029 R-15) ────────────────────

    /** 정본("지금 해소되는 전부")을 그대로 넘긴다. 목록에 없는 조합은 저장소가 지운다. */
    @Test
    fun `해소되는 쌍 전부를 정본으로 넘긴다`() {
        val resolved = listOf(
            CatalogLanguage(10L, ContentLanguage.EN),
            CatalogLanguage(11L, ContentLanguage.JA),
        )
        every { languagePort.replaceAll(any()) } returns 2

        service.syncCatalogLanguages(resolved)

        verify(exactly = 1) {
            languagePort.replaceAll(
                listOf(
                    CatalogLanguageEntry(10L, ContentLanguage.EN),
                    CatalogLanguageEntry(11L, ContentLanguage.JA),
                ),
            )
        }
    }

    /** 빈 정본도 건너뛰지 않고 반영한다. */
    @Test
    fun `정본이 비어 있어도 저장소에 반영한다`() {
        every { languagePort.replaceAll(any()) } returns 0

        service.syncCatalogLanguages(emptyList())

        verify(exactly = 1) { languagePort.replaceAll(emptyList()) }
    }

    /** 같은 정본이 두 번 와도 결과가 같다(멱등). */
    @Test
    fun `같은 정본을 두 번 받아도 같은 호출을 한다`() {
        val resolved = listOf(CatalogLanguage(10L, ContentLanguage.KO))
        every { languagePort.replaceAll(any()) } returns 1

        service.syncCatalogLanguages(resolved)
        service.syncCatalogLanguages(resolved)

        verify(exactly = 2) { languagePort.replaceAll(listOf(CatalogLanguageEntry(10L, ContentLanguage.KO))) }
    }
}
