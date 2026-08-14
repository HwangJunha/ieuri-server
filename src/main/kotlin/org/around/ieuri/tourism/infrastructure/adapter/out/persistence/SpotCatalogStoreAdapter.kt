package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.tourism.application.port.out.persistence.SpotCatalogStorePort
import org.around.ieuri.tourism.domain.aggregate.SpotCatalog
import org.around.ieuri.tourism.domain.vo.CatalogCandidate
import org.around.ieuri.tourism.domain.vo.DomainLocale
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

    /** 이름 키가 일치하는 카탈로그 후보. 순위 1·2가 쓴다. */
    @Transactional(readOnly = true)
    override fun findCatalogCandidatesByNameKey(nameKey: String): List<CatalogCandidate> =
        findCatalogCandidates(catalog.nameKey.eq(nameKey))

    /** 썸네일 URL이 일치하는 카탈로그 후보. 순위 3이 쓴다. */
    @Transactional(readOnly = true)
    override fun findCatalogCandidatesByThumbnail(thumbnailImageUrl: String): List<CatalogCandidate> =
        findCatalogCandidates(catalog.thumbnailImageUrl.eq(thumbnailImageUrl))

    /**
     * **좌표가 있는 카탈로그만 후보로 돌려준다.**
     *
     * 판정이 좌표 등치 비교뿐이라(CatalogMatchRule) 좌표 없는 카탈로그는 어차피 어떤 행과도
     * 일치하지 않는다. 그래서 여기서 거르는 편이 [CatalogCandidate]의 좌표를 nullable로 열어
     * 규칙 쪽에 "좌표 없음" 분기를 만드는 것보다 낫다.
     *
     * 감수하는 결과: 좌표 없는 카탈로그는 **후보가 하나뿐일 때의 순위 1 채택에서도 빠지고**,
     * 미해결 CSV의 후보 목록에도 나오지 않는다. 표준값 확정에 유효 좌표가 하나도 없었던
     * 카탈로그이므로 사람이 판정할 근거도 그만큼 없다.
     */
    private fun findCatalogCandidates(condition: com.querydsl.core.types.Predicate): List<CatalogCandidate> =
        queryFactory
            .select(catalog.id, catalog.mapX, catalog.mapY)
            .from(catalog)
            .where(catalog.mapX.isNotNull, catalog.mapY.isNotNull, condition)
            .fetch()
            .map {
                CatalogCandidate(
                    catalogId = requireNotNull(it.get(catalog.id)),
                    mapX = requireNotNull(it.get(catalog.mapX)),
                    mapY = requireNotNull(it.get(catalog.mapY)),
                )
            }

    /** 표준값 재확정. 집계 UPDATE라 생 SQL 어댑터가 담당한다. */
    override fun refreshStandardValues(catalogId: Long) = upsertAdapter.refreshStandardValues(catalogId)

    override fun recordUnresolved(
        spotId: Long,
        reason: UnresolvedReason,
        extractedKey: String?,
        candidateCatalogIds: List<Long>,
    ) = upsertAdapter.upsertUnresolved(spotId, reason, extractedKey, candidateCatalogIds)

    override fun upsertTranslation(
        catalogId: Long,
        language: DomainLocale,
        title: String,
        address: String?,
        source: TranslationSource,
    ) = upsertAdapter.upsertTranslation(catalogId, language, title, address, source)
}
