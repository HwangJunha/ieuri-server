package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.tourism.application.port.out.persistence.SpotCatalogStorePort
import org.around.ieuri.tourism.domain.aggregate.SpotCatalog
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.KoreanSpotCandidate
import org.around.ieuri.tourism.domain.vo.TranslationSource
import org.around.ieuri.tourism.domain.vo.UnresolvedReason
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotCatalogEntity
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
 * upsert가 필요한 두 경로(번역·미해결)만 [SpotCatalogUpsertJdbcAdapter]에 위임한다 —
 * JPA에 이식 가능한 upsert가 없고, `merge` 의미로는 select-then-insert 경합이 생긴다.
 */
@Component
class SpotCatalogStoreAdapter(
    private val queryFactory: JPAQueryFactory,
    private val catalogRepository: SpotCatalogJpaRepository,
    private val translationRepository: SpotCatalogTranslationJpaRepository,
    private val upsertAdapter: SpotCatalogUpsertJdbcAdapter,
) : SpotCatalogStorePort {

    private val spot = QSpotEntity.spotEntity
    private val catalog = QSpotCatalogEntity.spotCatalogEntity

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

    /**
     * 제목 정규화 키가 일치하는 KO 후보.
     *
     * **이미 카탈로그가 배정된 행만 후보다** — 배정되지 않은 KO 행에 붙이면 참조할 카탈로그가
     * 없다. 포트 계약이 KO를 먼저 처리하도록 정한 이유다.
     */
    @Transactional(readOnly = true)
    override fun findKoreanCandidatesByTitleKey(titleKey: String): List<KoreanSpotCandidate> =
        findKoreanCandidates(spot.titleKey.eq(titleKey))

    @Transactional(readOnly = true)
    override fun findKoreanCandidatesByThumbnail(thumbnailImageUrl: String): List<KoreanSpotCandidate> =
        findKoreanCandidates(spot.thumbnailImageUrl.eq(thumbnailImageUrl))

    private fun findKoreanCandidates(condition: com.querydsl.core.types.Predicate): List<KoreanSpotCandidate> =
        queryFactory
            .select(spot.id, spot.catalogId, spot.mapX, spot.mapY)
            .from(spot)
            .where(
                spot.language.eq(DomainLocale.KO),
                spot.catalogId.isNotNull,
                condition,
            )
            .fetch()
            .map {
                KoreanSpotCandidate(
                    spotId = requireNotNull(it.get(spot.id)),
                    catalogId = requireNotNull(it.get(spot.catalogId)),
                    mapX = requireNotNull(it.get(spot.mapX)),
                    mapY = requireNotNull(it.get(spot.mapY)),
                )
            }

    override fun recordUnresolved(
        spotId: Long,
        reason: UnresolvedReason,
        extractedKey: String?,
        candidateSpotIds: List<Long>,
    ) = upsertAdapter.upsertUnresolved(spotId, reason, extractedKey, candidateSpotIds)

    override fun upsertTranslation(
        catalogId: Long,
        language: DomainLocale,
        title: String,
        address: String?,
        source: TranslationSource,
    ) = upsertAdapter.upsertTranslation(catalogId, language, title, address, source)
}
