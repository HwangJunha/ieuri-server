package org.around.ieuri.tourism.infrastructure.adapter.out.query

import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.tourism.application.port.out.query.SpotCatalogQueryPort
import org.around.ieuri.tourism.application.port.out.query.ForeignCard
import org.around.ieuri.tourism.application.port.out.query.UnassignedSpot
import org.around.ieuri.tourism.application.port.out.query.UnresolvedCandidate
import org.around.ieuri.tourism.application.port.out.query.UnresolvedSpotReport
import org.around.ieuri.tourism.application.port.out.query.UntranslatedCatalog
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotCatalogEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotCatalogTranslationEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotEntity
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 카탈로그 배정·번역·보고의 읽기 포트 구현(SPEC-0019, CQRS 읽기).
 *
 * 미해결 보고만 [SpotCatalogUnresolvedQueryJdbcAdapter]에 위임한다 — `BIGINT[]` 컬럼을
 * JPA가 표현하지 못해 그 조회는 생 SQL이다.
 */
@Component
class SpotCatalogQueryAdapter(
    private val queryFactory: JPAQueryFactory,
    private val unresolvedAdapter: SpotCatalogUnresolvedQueryJdbcAdapter,
) : SpotCatalogQueryPort {

    private val spot = QSpotEntity.spotEntity
    private val catalog = QSpotCatalogEntity.spotCatalogEntity
    private val translation = QSpotCatalogTranslationEntity.spotCatalogTranslationEntity

    /**
     * 배정 대상 — `catalog_id`가 null인 행. 배정된 행은 다시 판정하지 않으므로 **이 조건
     * 하나가 곧 증분 처리다**(설계 문서 §3.3).
     *
     * **KO를 먼저 돌려준다** — KO 행이 카탈로그를 만들고 나머지 언어가 거기 붙으므로, 순서가
     * 뒤집히면 KO가 있는 장소인데도 파생 카탈로그가 생긴다. `language` 오름차순으로는 EN이
     * 앞서므로 정렬 식을 따로 만든다.
     */
    @Transactional(readOnly = true)
    override fun findUnassigned(limit: Int): List<UnassignedSpot> =
        queryFactory
            .select(
                spot.id, spot.language, spot.title, spot.address, spot.titleKey,
                spot.thumbnailImageUrl, spot.mapX, spot.mapY,
            )
            .from(spot)
            .where(spot.catalogId.isNull)
            .orderBy(
                // KO가 먼저. CASE 식이라 인덱스를 타지 않지만 대상 집합이 이미 좁다(미배정 행뿐).
                com.querydsl.core.types.dsl.Expressions
                    .cases()
                    .`when`(spot.language.eq(DomainLocale.KO)).then(0)
                    .otherwise(1)
                    .asc(),
                spot.id.asc(),
            )
            .limit(limit.toLong())
            .fetch()
            .map {
                UnassignedSpot(
                    spotId = requireNotNull(it.get(spot.id)),
                    language = requireNotNull(it.get(spot.language)),
                    title = requireNotNull(it.get(spot.title)),
                    address = it.get(spot.address),
                    titleKey = it.get(spot.titleKey),
                    thumbnailImageUrl = requireNotNull(it.get(spot.thumbnailImageUrl)),
                    mapX = requireNotNull(it.get(spot.mapX)),
                    mapY = requireNotNull(it.get(spot.mapY)),
                )
            }

    /**
     * 같은 썸네일을 쓰는 비한국어 행들의 제목. 파생 대표명을 EN→JA→ZH 순으로 고르는 입력이다.
     *
     * 한 언어에 여러 행이 걸리면 첫 행을 쓴다 — 대표명 후보로서 어느 쪽이든 같은 괄호값을 갖는다.
     */
    @Transactional(readOnly = true)
    override fun findForeignCardsByThumbnail(thumbnailImageUrl: String): Map<DomainLocale, ForeignCard> =
        queryFactory
            .select(spot.language, spot.title, spot.address)
            .from(spot)
            .where(
                spot.thumbnailImageUrl.eq(thumbnailImageUrl),
                spot.language.ne(DomainLocale.KO),
            )
            .fetch()
            .associate {
                requireNotNull(it.get(spot.language)) to
                    ForeignCard(requireNotNull(it.get(spot.title)), it.get(spot.address))
            }

    /**
     * 번역이 비어 있는 카탈로그.
     *
     * 카탈로그별로 **이미 가진 언어**를 모아 요청 언어에서 빼는 방식이다. "없는 것"을 SQL로
     * 직접 구하려면 언어 수만큼 NOT EXISTS가 붙어 조건이 입력 개수에 비례해 늘어난다.
     */
    @Transactional(readOnly = true)
    override fun findCatalogsMissingTranslation(
        languages: Set<DomainLocale>,
        limit: Int,
    ): List<UntranslatedCatalog> {
        if (languages.isEmpty()) return emptyList()

        // 요청 언어를 전부 가진 카탈로그는 애초에 제외한다 — 그래야 limit이 실제 대상으로 채워진다.
        val candidateIds = queryFactory
            .select(catalog.id)
            .from(catalog)
            .leftJoin(translation)
            .on(translation.catalogId.eq(catalog.id), translation.language.`in`(languages))
            .groupBy(catalog.id)
            .having(translation.id.count().lt(languages.size.toLong()))
            .orderBy(catalog.id.asc())
            .limit(limit.toLong())
            .fetch()

        if (candidateIds.isEmpty()) return emptyList()

        // 번역 원문은 KO 카드다 — 제목은 대표명, 주소는 KO 번역 행의 주소다(R-4).
        val koTranslation = QSpotCatalogTranslationEntity("koTranslation")
        val names = queryFactory
            .select(catalog.id, catalog.representativeNameKo, koTranslation.address)
            .from(catalog)
            .leftJoin(koTranslation)
            .on(koTranslation.catalogId.eq(catalog.id), koTranslation.language.eq(DomainLocale.KO))
            .where(catalog.id.`in`(candidateIds))
            .fetch()
            .associate {
                requireNotNull(it.get(catalog.id)) to
                    (requireNotNull(it.get(catalog.representativeNameKo)) to it.get(koTranslation.address))
            }

        val existingByCatalog = queryFactory
            .select(translation.catalogId, translation.language)
            .from(translation)
            .where(translation.catalogId.`in`(candidateIds))
            .fetch()
            .groupBy({ requireNotNull(it.get(translation.catalogId)) }) { requireNotNull(it.get(translation.language)) }

        return candidateIds.mapNotNull { catalogId ->
            val missing = languages - existingByCatalog[catalogId].orEmpty().toSet()
            if (missing.isEmpty()) return@mapNotNull null

            val (nameKo, addressKo) = names[catalogId] ?: return@mapNotNull null
            UntranslatedCatalog(
                catalogId = catalogId,
                representativeNameKo = nameKo,
                representativeAddressKo = addressKo,
                missingLanguages = missing,
            )
        }
    }

    @Transactional(readOnly = true)
    override fun findUnresolved(): List<UnresolvedSpotReport> = unresolvedAdapter.findUnresolved()
}
