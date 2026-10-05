package org.around.ieuri.community.adapter.event.listener

import org.around.ieuri.community.application.catalog.provided.SpotCatalogSyncUseCase
import org.around.ieuri.tourism.domain.event.SpotCatalogDeleted
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 카탈로그 삭제(커밋 후)를 받아 글-장소 연결을 정리한다(SPEC-0023 R11).
 * - 예외를 삼키고 로그만 남긴다 — 던지면 같은 이벤트의 다른 도메인 리스너까지 막힌다.
 * - 현재 [SpotCatalogDeleted]를 발행하는 곳이 없다 — 삭제 경로가 생기면 발행만 추가하면 된다.
 */
@Component
class CommunitySpotCatalogDeletedListener(
    private val spotCatalogSyncUseCase: SpotCatalogSyncUseCase,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: SpotCatalogDeleted) {
        try {
            spotCatalogSyncUseCase.cleanupDeletedCatalogs(event.catalogIds)
        } catch (e: Exception) {
            log.error("failed to clean community post-spot links for {} deleted catalogs", event.catalogIds.size, e)
        }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(CommunitySpotCatalogDeletedListener::class.java)
    }
}
