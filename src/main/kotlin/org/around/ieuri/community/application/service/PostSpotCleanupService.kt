package org.around.ieuri.community.application.service

import org.around.ieuri.community.application.port.`in`.command.CatalogLanguage
import org.around.ieuri.community.application.port.`in`.command.PostSpotCleanupUseCase
import org.around.ieuri.community.application.port.out.persistence.CatalogLanguageEntry
import org.around.ieuri.community.application.port.out.persistence.PostSpotCleanupPort
import org.around.ieuri.community.application.port.out.persistence.SpotCatalogLanguagePort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 사라진 장소를 가리키는 글-장소 연결 정리와 카탈로그-언어 사본 동기화(SPEC-0023, SPEC-0029).
 * - 글은 지우지 않고 연결만 끊는다. 연결이 끊긴 글은 공간 검색에서 빠지고 목록·상세에는 남는다.
 */
@Service
class PostSpotCleanupService(
    private val cleanupPort: PostSpotCleanupPort,
    private val languagePort: SpotCatalogLanguagePort,
) : PostSpotCleanupUseCase {

    /**
     * `AFTER_COMMIT` 리스너가 부르므로 REQUIRES_NEW여야 한다.
     * - REQUIRED면 이미 커밋된 원본 트랜잭션에 합류해 예외 없이 아무것도 반영되지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun cleanupDeletedCatalogs(catalogIds: List<Long>) {
        if (catalogIds.isEmpty()) return

        val deleted = cleanupPort.deleteByCatalogIds(catalogIds)
        log.info("community post-spot links cleaned for {} deleted catalogs: links={}", catalogIds.size, deleted)
    }

    /**
     * 카탈로그-언어 사본을 정본 전체로 덮어쓴다(SPEC-0029). [resolved]에 없는 조합은 지운다.
     * - 빈 목록이어도 반영한다 — 사본이 전부 지워진다.
     * - [cleanupDeletedCatalogs]와 같은 이유로 REQUIRES_NEW다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun syncCatalogLanguages(resolved: List<CatalogLanguage>) {
        val total = languagePort.replaceAll(resolved.map { it.toEntry() })
        log.info("community spot catalog languages synced: rows={}", total)
    }

    private fun CatalogLanguage.toEntry() = CatalogLanguageEntry(catalogId, language)

    private companion object {
        private val log = LoggerFactory.getLogger(PostSpotCleanupService::class.java)
    }
}
