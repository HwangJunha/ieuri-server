package org.around.ieuri.community.application.catalog

import org.around.ieuri.community.application.catalog.provided.SpotCatalogSyncUseCase
import org.around.ieuri.community.application.catalog.required.SpotCatalogLanguageRepository
import org.around.ieuri.community.application.post.required.PostRepository
import org.around.ieuri.community.domain.catalog.CatalogLanguage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 사라진 장소의 글-장소 연결 정리와 카탈로그-언어 사본 동기화. 연결이 끊긴 글은 공간 검색에서만 빠진다.
 * - `AFTER_COMMIT` 리스너가 부르므로 REQUIRES_NEW다 — REQUIRED면 커밋된 원본 트랜잭션에 합류해 아무것도 반영되지 않는다.
 */
@Service
@Transactional(propagation = Propagation.REQUIRES_NEW)
class SpotCatalogSyncService(
    private val postRepository: PostRepository,
    private val catalogLanguageRepository: SpotCatalogLanguageRepository,
) : SpotCatalogSyncUseCase {

    override fun cleanupDeletedCatalogs(catalogIds: List<Long>) {
        if (catalogIds.isEmpty()) return

        val detached = postRepository.detachSpotsOfCatalogs(catalogIds)
        log.info("community post-spot links cleaned for {} deleted catalogs: links={}", catalogIds.size, detached)
    }

    override fun syncCatalogLanguages(resolved: List<CatalogLanguage>) {
        val total = catalogLanguageRepository.replaceAll(resolved)
        log.info("community spot catalog languages synced: rows={}", total)
    }

    private companion object {
        private val log = LoggerFactory.getLogger(SpotCatalogSyncService::class.java)
    }
}
