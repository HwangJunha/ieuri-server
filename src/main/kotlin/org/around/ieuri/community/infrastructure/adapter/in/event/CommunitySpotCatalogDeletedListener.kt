package org.around.ieuri.community.infrastructure.adapter.`in`.event

import org.around.ieuri.community.application.port.`in`.command.PostSpotCleanupUseCase
import org.around.ieuri.tourism.domain.event.SpotCatalogDeleted
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 카탈로그 삭제를 받아 글-장소 연결을 정리한다(SPEC-0023 R11). **인바운드 어댑터다** —
 * tourism이 community를 호출하는 것이 아니라 community가 이벤트를 구독한다.
 *
 * `CommunityMemberWithdrawnListener`와 같은 형태이며 이유도 같다:
 *  - `AFTER_COMMIT` — 삭제가 확정된 뒤에만 정리한다.
 *  - **예외를 삼킨다** — 커밋 뒤라 되돌릴 수 없고, 던지면 같은 이벤트를 받는 다른 도메인의
 *    리스너까지 막힌다.
 *  - **클래스 이름에 도메인을 붙인다** — Spring이 단순 클래스명으로 빈 이름을 지어, 다른
 *    도메인의 같은 리스너와 겹치면 패키지가 달라도 기동이 실패한다.
 *
 * **오늘 이 리스너를 깨우는 발행 지점이 없다**(SpotCatalogDeleted 주석). 카탈로그 삭제 경로가
 * 생기는 시점에 발행만 추가하면 동작한다.
 */
@Component
class CommunitySpotCatalogDeletedListener(
    private val postSpotCleanupUseCase: PostSpotCleanupUseCase,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: SpotCatalogDeleted) {
        try {
            postSpotCleanupUseCase.cleanupDeletedCatalogs(event.catalogIds)
        } catch (e: Exception) {
            log.error("failed to clean community post-spot links for {} deleted catalogs", event.catalogIds.size, e)
        }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(CommunitySpotCatalogDeletedListener::class.java)
    }
}
