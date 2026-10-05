package org.around.ieuri.community.application.catalog

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.community.application.catalog.required.SpotCatalogLanguageRepository
import org.around.ieuri.community.application.post.required.PostRepository
import org.around.ieuri.community.domain.catalog.CatalogLanguage
import org.around.ieuri.community.domain.shared.ContentLanguage
import kotlin.test.Test

class SpotCatalogSyncServiceTest {

    private val posts = mockk<PostRepository>()
    private val languages = mockk<SpotCatalogLanguageRepository>()
    private val service = SpotCatalogSyncService(posts, languages)

    @Test
    fun `사라진 카탈로그가 없으면 저장소를 부르지 않는다`() {
        service.cleanupDeletedCatalogs(emptyList())

        verify(exactly = 0) { posts.detachSpotsOfCatalogs(any()) }
    }

    @Test
    fun `사라진 카탈로그의 글-장소 연결을 끊는다`() {
        every { posts.detachSpotsOfCatalogs(listOf(1, 2)) } returns 4

        service.cleanupDeletedCatalogs(listOf(1, 2))

        verify { posts.detachSpotsOfCatalogs(listOf(1, 2)) }
    }

    @Test
    fun `사본은 정본 전체로 바꾸며 빈 정본도 그대로 넘긴다`() {
        val resolved = listOf(CatalogLanguage(1, ContentLanguage.KO), CatalogLanguage(1, ContentLanguage.EN))
        every { languages.replaceAll(any()) } returns 0

        service.syncCatalogLanguages(resolved)
        service.syncCatalogLanguages(emptyList())

        verify { languages.replaceAll(resolved) }
        verify { languages.replaceAll(emptyList()) }
    }
}
