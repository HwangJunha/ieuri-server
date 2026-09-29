package org.around.ieuri.community.infrastructure.adapter.`in`.event

import org.around.ieuri.community.application.port.`in`.command.CatalogLanguage
import org.around.ieuri.community.application.port.`in`.command.PostSpotCleanupUseCase
import org.around.ieuri.community.domain.vo.ContentLanguage
import org.around.ieuri.tourism.domain.event.CatalogLanguageRef
import org.around.ieuri.tourism.domain.event.SpotCatalogLanguagesChanged
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 카탈로그의 언어별 노출 변화(커밋 후)를 받아 커뮤니티 사본을 맞춘다(SPEC-0029 R-15·R-23).
 * - 예외를 삼키고 로그만 남긴다. 어긋난 사본은 다음 회차의 전체 집합으로 복구된다.
 * - 이벤트를 커뮤니티 타입으로 옮겨 넘긴다. 모르는 언어 코드는 버린다.
 */
@Component
class CommunitySpotCatalogLanguagesChangedListener(
    private val postSpotCleanupUseCase: PostSpotCleanupUseCase,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: SpotCatalogLanguagesChanged) {
        try {
            postSpotCleanupUseCase.syncCatalogLanguages(event.resolved.toCatalogLanguages())
        } catch (e: Exception) {
            log.error(
                "failed to sync community spot catalog languages: resolved={}",
                event.resolved.size,
                e,
            )
        }
    }

    private fun List<CatalogLanguageRef>.toCatalogLanguages(): List<CatalogLanguage> = mapNotNull { ref ->
        ContentLanguage.entries.firstOrNull { it.name == ref.languageCode }
            ?.let { CatalogLanguage(catalogId = ref.catalogId, language = it) }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(CommunitySpotCatalogLanguagesChangedListener::class.java)
    }
}
