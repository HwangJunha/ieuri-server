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
 * 카탈로그의 언어별 노출 변화를 받아 커뮤니티 사본을 맞춘다(SPEC-0029 R-15·R-23).
 * **인바운드 어댑터다** — tourism이 community를 호출하는 것이 아니라 community가 구독한다.
 *
 * `CommunitySpotCatalogDeletedListener`와 같은 형태이며 이유도 같다:
 *  - `AFTER_COMMIT` — 노출 갱신이 확정된 뒤에만 사본을 고친다.
 *  - **예외를 삼킨다** — 커밋 뒤라 되돌릴 수 없고, 던지면 같은 이벤트를 받는 다른 도메인의
 *    리스너까지 막힌다. 사본이 어긋난 채 남지만 **다음 배정 회차가 정본을 다시 보내므로
 *    자연히 복구된다** — 델타였다면 놓친 변화가 영구히 남았을 것이다.
 *  - **클래스 이름에 도메인을 붙인다** — Spring이 단순 클래스명으로 빈 이름을 지어, 다른
 *    도메인의 같은 리스너와 겹치면 패키지가 달라도 기동이 실패한다.
 *
 * **이벤트 타입을 유스케이스로 넘기지 않는다.** 여기서 커뮤니티 소유 타입으로 옮겨 담아야
 * application이 tourism을 모르는 채로 남는다 — 언어 코드 문자열을 [ContentLanguage]로 되돌리는
 * 것도 이 어댑터의 몫이다. 모르는 코드는 버린다(그 언어를 커뮤니티가 아직 모른다는 뜻이다).
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
                event.resolved.size, e,
            )
        }
    }

    private fun List<CatalogLanguageRef>.toCatalogLanguages(): List<CatalogLanguage> =
        mapNotNull { ref ->
            ContentLanguage.entries.firstOrNull { it.name == ref.languageCode }
                ?.let { CatalogLanguage(catalogId = ref.catalogId, language = it) }
        }

    private companion object {
        private val log = LoggerFactory.getLogger(CommunitySpotCatalogLanguagesChangedListener::class.java)
    }
}
