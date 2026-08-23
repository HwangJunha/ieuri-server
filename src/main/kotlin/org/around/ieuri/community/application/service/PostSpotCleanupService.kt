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
 * 사라진 장소를 가리키는 글-장소 연결 정리(SPEC-0023 R11).
 *
 * **글은 지우지 않고 연결만 끊는다.** `community_post_spot`은 글 본문이 아니라 연결
 * 테이블이므로, 장소가 사라지면 연결만 사라지는 것이 자연스럽다. `catalog_id`가 NOT NULL이라
 * null로 비우는 선택지는 없다.
 *
 * **알려진 결과**: `PostSpatialQueryAdapter`가 글→연결을 조인하므로 연결이 끊긴 글은 **공간
 * 검색 결과에서 빠진다.** 목록·상세에는 그대로 남는다. 장소 기반 검색의 의미상 타당한 동작이다.
 */
@Service
class PostSpotCleanupService(
    private val cleanupPort: PostSpotCleanupPort,
    private val languagePort: SpotCatalogLanguagePort,
) : PostSpotCleanupUseCase {

    /**
     * **REQUIRES_NEW여야 한다.** `AFTER_COMMIT` 리스너가 부르므로 원본 트랜잭션은 이미
     * 커밋됐으면서도 스레드에 묶여 있다 — 기본 전파로 두면 커밋할 수 없는 그 트랜잭션에
     * 합류해 삭제가 흘러가지 않고, 예외도 없어 성공한 것처럼 보인다
     * (`MemberDataEraseService`가 같은 이유로 같은 설정을 쓴다).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun cleanupDeletedCatalogs(catalogIds: List<Long>) {
        // 빈 목록이면 저장소를 건드리지 않는다. `IN ()`은 어차피 0건이지만 왕복을 아낀다.
        if (catalogIds.isEmpty()) return

        val deleted = cleanupPort.deleteByCatalogIds(catalogIds)
        log.info("community post-spot links cleaned for {} deleted catalogs: links={}", catalogIds.size, deleted)
    }

    /**
     * 카탈로그-언어 사본을 **정본으로 덮어쓴다**(SPEC-0029 R-15).
     *
     * [resolved]에 없는 조합은 지운다 — 이벤트가 변화분이 아니라 현재 상태 전부를 싣기
     * 때문이다. 델타를 적용하는 방식은 빈 표를 채우지 못한다: 전량 재적재 직후에는 모든
     * 행이 신규라 뒤집힌 것이 없어 델타가 비고, 그러면 목록이 spot 붙은 글·질문을 전부
     * 조용히 걸러 낸다.
     *
     * **비어 있어도 반영한다** — 원본이 정말 비었다는 뜻일 수 있고, 그때 사본만 남으면
     * 사라진 장소의 글이 계속 보인다.
     *
     * [cleanupDeletedCatalogs]와 같은 이유로 REQUIRES_NEW다 — AFTER_COMMIT 리스너가 부른다.
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
