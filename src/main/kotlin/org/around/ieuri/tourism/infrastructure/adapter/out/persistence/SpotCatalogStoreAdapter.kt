package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.tourism.application.port.out.persistence.CatalogLanguage
import org.around.ieuri.tourism.application.port.out.persistence.SpotCatalogStorePort
import org.around.ieuri.tourism.application.port.out.persistence.VisibilityRefreshResult
import org.around.ieuri.tourism.domain.aggregate.SpotCatalog
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotCatalogTranslationEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.mapper.toNewEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.mapper.toNewTranslationEntities
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.repository.SpotCatalogJpaRepository
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.repository.SpotCatalogTranslationJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 카탈로그 쓰기 포트 구현(SPEC-0019). JPA·QueryDSL로 표현되는 쓰기만 있다.
 * - upsert는 `SpotCatalogUpsertJdbcAdapter`가 맡고 서비스가 두 포트를 따로 주입받는다.
 */
@Component
class SpotCatalogStoreAdapter(
    private val queryFactory: JPAQueryFactory,
    private val catalogRepository: SpotCatalogJpaRepository,
    private val translationRepository: SpotCatalogTranslationJpaRepository,
) : SpotCatalogStorePort {

    private val spot = QSpotEntity.spotEntity
    private val translation = QSpotCatalogTranslationEntity.spotCatalogTranslationEntity

    /** 카탈로그와 번역을 함께 저장한다. 부모를 먼저 flush해야 자식의 `catalog_id`가 정해진다. */
    @Transactional
    override fun save(catalog: SpotCatalog): Long {
        val saved = catalogRepository.saveAndFlush(catalog.toNewEntity())
        val catalogId = requireNotNull(saved.id) { "저장 직후 카탈로그 id가 없다." }

        translationRepository.saveAll(catalog.toNewTranslationEntities(catalogId))
        return catalogId
    }

    /**
     * spot을 카탈로그에 배정한다. 이미 배정된 행은 덮어쓰지 않는다 — 덮으면 좋아요가 다른 장소로 옮겨 간다.
     * - 벌크 업데이트라 영속성 컨텍스트를 우회한다(같은 트랜잭션에서 다시 읽지 않는다).
     */
    @Transactional
    override fun assign(spotId: Long, catalogId: Long) {
        queryFactory
            .update(spot)
            .set(spot.catalogId, catalogId)
            .where(spot.id.eq(spotId), spot.catalogId.isNull)
            .execute()
    }

    /**
     * 그 언어의 원본 생존 여부를 번역 행의 `visible`에 반영하고(양방향), 반영 후 노출 중인 것 전부를 돌려준다(SPEC-0029).
     * - 돌려주는 것은 변경분이 아니라 현재 상태 전부다 — 구독자의 사본이 빈 상태에서도 채워진다.
     * - 상태가 실제로 바뀐 행만 갱신한다. 벌크 갱신이라 영속성 컨텍스트를 우회한다.
     */
    @Transactional
    override fun refreshTranslationVisibility(): VisibilityRefreshResult {
        // 그 언어의 원본이 있는가 = 노출 여부.
        val originExists = JPAExpressions.selectOne().from(spot)
            .where(spot.catalogId.eq(translation.catalogId), spot.language.eq(translation.language))
            .exists()

        val hidden = queryFactory
            .update(translation)
            .set(translation.visible, false)
            .where(translation.visible.isTrue, originExists.not())
            .execute()

        val shown = queryFactory
            .update(translation)
            .set(translation.visible, true)
            .where(translation.visible.isFalse, originExists)
            .execute()

        return VisibilityRefreshResult(
            hiddenCount = hidden.toInt(),
            shownCount = shown.toInt(),
            visible = findVisible(),
        )
    }

    /** 지금 노출 중인 (카탈로그, 언어) 전부. 구독자가 사본을 맞추는 정본이다. */
    private fun findVisible(): List<CatalogLanguage> = queryFactory
        .select(translation.catalogId, translation.language)
        .from(translation)
        .where(translation.visible.isTrue)
        .fetch()
        .map {
            CatalogLanguage(
                catalogId = requireNotNull(it.get(translation.catalogId)),
                language = requireNotNull(it.get(translation.language)),
            )
        }
}
