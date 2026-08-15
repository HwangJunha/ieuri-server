package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.tourism.application.port.out.persistence.SpotCatalogStorePort
import org.around.ieuri.tourism.application.port.out.persistence.SpotCatalogUpsertPort
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
 * 카탈로그 쓰기 포트 구현(SPEC-0019). 도메인↔엔티티 변환은 `mapper` 패키지가 담당한다.
 *
 * upsert가 필요한 두 경로(번역·축제기간)만 [SpotCatalogUpsertPort]에 위임한다 —
 * JPA에 이식 가능한 upsert가 없고, `merge` 의미로는 select-then-insert 경합이 생긴다.
 */
@Component
class SpotCatalogStoreAdapter(
    private val queryFactory: JPAQueryFactory,
    private val catalogRepository: SpotCatalogJpaRepository,
    private val translationRepository: SpotCatalogTranslationJpaRepository,
    private val upsertPort: SpotCatalogUpsertPort,
) : SpotCatalogStorePort {

    private val spot = QSpotEntity.spotEntity
    private val translation = QSpotCatalogTranslationEntity.spotCatalogTranslationEntity

    /**
     * 카탈로그와 번역을 함께 저장한다.
     *
     * **부모를 먼저 flush해야 한다** — 자식이 들고 갈 `catalog_id`가 그때 정해진다.
     */
    @Transactional
    override fun save(catalogToSave: SpotCatalog): Long {
        val saved = catalogRepository.saveAndFlush(catalogToSave.toNewEntity())
        val catalogId = requireNotNull(saved.id) { "저장 직후 카탈로그 id가 없다." }

        translationRepository.saveAll(catalogToSave.toNewTranslationEntities(catalogId))
        return catalogId
    }

    /**
     * spot을 카탈로그에 배정한다. **이미 배정된 행은 덮어쓰지 않는다** — 재실행이 기존 배정을
     * 흔들면 좋아요가 딸려 다른 장소로 옮겨 간다.
     *
     * 벌크 업데이트라 영속성 컨텍스트를 우회한다. 이 트랜잭션에서 같은 엔티티를 다시 읽는
     * 코드가 없으므로 안전하다.
     */
    @Transactional
    override fun assign(spotId: Long, catalogId: Long) {
        queryFactory
            .update(spot)
            .set(spot.catalogId, catalogId)
            .where(spot.id.eq(spotId), spot.catalogId.isNull)
            .execute()
    }

    override fun upsertTranslation(catalogId: Long, language: DomainLocale, title: String, address: String?) =
        upsertPort.upsertTranslation(catalogId, language, title, address)

    override fun refreshFestivalPeriod(catalogId: Long) = upsertPort.refreshFestivalPeriod(catalogId)

    /**
     * 원본 생존 여부를 번역 행의 `visible`에 반영한다. 양방향이다 — 사라진 것은 감추고
     * 돌아온 것은 다시 보이게 한다.
     *
     * 벌크 갱신이라 영속성 컨텍스트를 우회한다. 이 트랜잭션에서 같은 엔티티를 다시 읽는
     * 코드가 없으므로 안전하다. **상태가 실제로 바뀐 행만 건드린다** — 매 실행마다 전 행을
     * 갱신하면 그 UPDATE가 전부 복제로 흘러가 검색 노드가 불필요하게 출렁인다.
     */
    @Transactional
    override fun refreshTranslationVisibility(): Int {
        val originExists = JPAExpressions.selectOne().from(spot)
            .where(spot.catalogId.eq(translation.catalogId))
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

        return (hidden + shown).toInt()
    }
}
