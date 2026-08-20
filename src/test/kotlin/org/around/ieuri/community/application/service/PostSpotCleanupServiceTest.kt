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

/**
 * 사라진 장소를 가리키는 글-장소 연결 정리(SPEC-0023 R11, AC-8·AC-9).
 *
 * 저장소 포트를 스텁으로 두고 핸들러만 검증한다 — 실제 삭제 반영은 DB에서만 관찰 가능하며
 * 인프라 관심사다.
 */
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

        // 넘긴 목록 그대로 한 번만 — 다른 카탈로그(예: 12L)는 인자에 없으므로 건드릴 수 없다.
        verify(exactly = 1) { cleanupPort.deleteByCatalogIds(deleted) }
    }

    /**
     * 한 장소를 여러 글이 참조하므로 정리는 여러 행에 걸친다. 단건 가정을 두지 않는다는 것이
     * 목록을 그대로 넘기는 이 설계의 요지다.
     */
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

    /**
     * AC-9 — 가리키는 참조가 하나도 없어 0건이 지워져도 정상이다. 삭제된 장소를 아무 글도
     * 참조하지 않는 것은 흔한 경우이며 오류가 아니다.
     */
    @Test
    fun `지운 연결이 0건이어도 예외가 아니다`() {
        val deleted = listOf(42L)
        every { cleanupPort.deleteByCatalogIds(deleted) } returns 0L

        service.cleanupDeletedCatalogs(deleted)

        verify(exactly = 1) { cleanupPort.deleteByCatalogIds(deleted) }
    }

    // ── 카탈로그-언어 사본 동기화(SPEC-0029 R-15) ────────────────────

    /**
     * **정본을 그대로 넘긴다.** 이벤트가 싣는 것은 변화분이 아니라 "지금 해소되는 전부"이며,
     * 저장소가 그 목록에 없는 조합을 지운다.
     */
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

    /**
     * **빈 정본도 반영한다.** 이것이 이 슬라이스에서 가장 조용히 틀렸던 지점이다 — 변화분만
     * 싣던 설계에서는 전량 재적재 직후 모든 행이 신규 INSERT라 뒤집힌 것이 없어 이벤트가 비었고,
     * 그 빈 이벤트를 "할 일 없음"으로 건너뛰면 사본이 영원히 빈 채로 남아 목록이 spot 붙은
     * 글·질문을 전부 조용히 걸러 냈다.
     *
     * 정본 방식에서 빈 목록은 "해소되는 것이 없다"는 사실이고, 그것도 반영해야 한다 —
     * 원본이 비었는데 사본만 남으면 사라진 장소의 글이 계속 보인다.
     */
    @Test
    fun `정본이 비어 있어도 저장소에 반영한다`() {
        every { languagePort.replaceAll(any()) } returns 0

        service.syncCatalogLanguages(emptyList())

        verify(exactly = 1) { languagePort.replaceAll(emptyList()) }
    }

    /** 같은 정본이 두 번 와도 결과가 같다 — 배치가 매 회차 발행하므로 멱등이어야 한다. */
    @Test
    fun `같은 정본을 두 번 받아도 같은 호출을 한다`() {
        val resolved = listOf(CatalogLanguage(10L, ContentLanguage.KO))
        every { languagePort.replaceAll(any()) } returns 1

        service.syncCatalogLanguages(resolved)
        service.syncCatalogLanguages(resolved)

        verify(exactly = 2) { languagePort.replaceAll(listOf(CatalogLanguageEntry(10L, ContentLanguage.KO))) }
    }
}
