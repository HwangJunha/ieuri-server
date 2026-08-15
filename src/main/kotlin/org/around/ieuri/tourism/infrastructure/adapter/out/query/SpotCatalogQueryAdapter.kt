package org.around.ieuri.tourism.infrastructure.adapter.out.query

import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.tourism.application.port.out.query.CatalogCandidate
import org.around.ieuri.tourism.application.port.out.query.SpotCatalogQueryPort
import org.around.ieuri.tourism.application.port.out.query.UnassignedSpot
import org.around.ieuri.tourism.application.port.out.query.UntranslatedCatalog
import org.around.ieuri.tourism.domain.aggregate.CatalogStandardValues
import org.around.ieuri.tourism.domain.specification.SpotTitleNormalizer
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotCatalogEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotCatalogTranslationEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotEntity
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 카탈로그 배정·번역의 읽기 포트 구현(SPEC-0019, CQRS 읽기).
 */
@Component
class SpotCatalogQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : SpotCatalogQueryPort {

    private val spot = QSpotEntity.spotEntity
    private val catalog = QSpotCatalogEntity.spotCatalogEntity
    private val translation = QSpotCatalogTranslationEntity.spotCatalogTranslationEntity

    /**
     * 배정 대상 — `catalog_id`가 null이고 좌표가 유효한 행. 배정된 행은 다시 판정하지 않으므로
     * **미배정 조건 하나가 곧 증분 처리다**(설계 문서 §3.3).
     *
     * **좌표 필터가 여기 있다.** (0,0)이거나 한국 밖인 행은 깨진 데이터이며(실측 317건)
     * 카탈로그를 내주면 지도·반경 조회에서 빠지는 "존재하지만 안 보이는" 데이터가 된다.
     */
    @Transactional(readOnly = true)
    override fun findUnassigned(language: DomainLocale, afterSpotId: Long, limit: Int): List<UnassignedSpot> =
        queryFactory
            .select(
                spot.id, spot.language, spot.title, spot.address,
                spot.thumbnailImageUrl, spot.mapX, spot.mapY,
                spot.category, spot.copyright,
            )
            .from(spot)
            .where(
                spot.catalogId.isNull,
                spot.language.eq(language),
                spot.id.gt(afterSpotId),
                spot.mapX.between(
                    CatalogStandardValues.KOREA_LON_RANGE.start,
                    CatalogStandardValues.KOREA_LON_RANGE.endInclusive,
                ),
                spot.mapY.between(
                    CatalogStandardValues.KOREA_LAT_RANGE.start,
                    CatalogStandardValues.KOREA_LAT_RANGE.endInclusive,
                ),
            )
            .orderBy(spot.id.asc())
            .limit(limit.toLong())
            .fetch()
            .map {
                UnassignedSpot(
                    spotId = requireNotNull(it.get(spot.id)),
                    language = requireNotNull(it.get(spot.language)),
                    title = requireNotNull(it.get(spot.title)),
                    address = it.get(spot.address),
                    thumbnailImageUrl = requireNotNull(it.get(spot.thumbnailImageUrl)),
                    mapX = requireNotNull(it.get(spot.mapX)),
                    mapY = requireNotNull(it.get(spot.mapY)),
                    category = SpotCategory(requireNotNull(it.get(spot.category))),
                    copyright = requireNotNull(it.get(spot.copyright)),
                )
            }

    /**
     * KO 카탈로그의 제목 키 → 후보 맵. 배정 회차마다 한 번 읽어 메모리에서 대조한다.
     *
     * **정규화를 SQL로 하지 않는다** — 규칙은 도메인([SpotTitleNormalizer])이 소유하며, DB
     * 함수로 옮기면 규칙이 두 곳에 생겨 반드시 어긋난다. 제목을 그대로 읽어 와 여기서 키를
     * 만들기 때문에 저장 컬럼도 인덱스도 필요 없다.
     *
     * KO 카탈로그 수만큼(실측 56,413) 메모리를 쓴다 — 키가 평균 수십 바이트라 수 MB 수준이다.
     */
    @Transactional(readOnly = true)
    override fun loadKoreanTitleIndex(): Map<String, List<CatalogCandidate>> =
        queryFactory
            .select(catalog.id, catalog.mapX, catalog.mapY, translation.title)
            .from(translation)
            .join(catalog).on(catalog.id.eq(translation.catalogId))
            .where(translation.language.eq(DomainLocale.KO))
            .fetch()
            .mapNotNull { row ->
                // **배정 쪽과 같은 함수여야 한다** — 규칙이 갈리면 맵의 키와 조회 키가 어긋난다.
                val key = SpotTitleNormalizer.lookupKey(requireNotNull(row.get(translation.title)))
                    ?: return@mapNotNull null
                key to CatalogCandidate(
                    catalogId = requireNotNull(row.get(catalog.id)),
                    mapX = requireNotNull(row.get(catalog.mapX)),
                    mapY = requireNotNull(row.get(catalog.mapY)),
                )
            }
            .groupBy({ it.first }) { it.second }

    /**
     * 썸네일 URL → KO 카탈로그 id. **KO spot이 정확히 하나인 URL만 담는다.**
     *
     * `HAVING count = 1`이 이 조회의 전부다 — 여럿이 공유하는 URL은 같은 장소라는 근거가
     * 되지 못하므로 애초에 후보에서 뺀다.
     */
    @Transactional(readOnly = true)
    override fun loadKoreanThumbnailIndex(): Map<String, Long> =
        queryFactory
            .select(spot.thumbnailImageUrl, spot.catalogId.min())
            .from(spot)
            .where(
                spot.language.eq(DomainLocale.KO),
                spot.catalogId.isNotNull,
                spot.thumbnailImageUrl.isNotEmpty,
            )
            .groupBy(spot.thumbnailImageUrl)
            .having(spot.id.count().eq(1L))
            .fetch()
            .associate {
                requireNotNull(it.get(spot.thumbnailImageUrl)) to requireNotNull(it.get(spot.catalogId.min()))
            }

    /**
     * 번역이 비어 있는 카탈로그.
     *
     * 카탈로그별로 **이미 가진 언어**를 모아 요청 언어에서 빼는 방식이다. "없는 것"을 SQL로
     * 직접 구하려면 언어 수만큼 NOT EXISTS가 붙어 조건이 입력 개수에 비례해 늘어난다.
     *
     * **KO 번역이 있는 카탈로그만 대상이다** — 번역 원문이 KO이므로, 외국어에만 있는 장소는
     * 번역할 원문 자체가 없다. 그 장소는 자기 언어 사용자에게만 보이며 그것이 올바른 동작이다.
     */
    @Transactional(readOnly = true)
    override fun findCatalogsMissingTranslation(
        languages: Set<DomainLocale>,
        limit: Int,
    ): List<UntranslatedCatalog> {
        if (languages.isEmpty()) return emptyList()

        val koTranslation = QSpotCatalogTranslationEntity("koTranslation")

        // 요청 언어를 전부 가진 카탈로그는 애초에 제외한다 — 그래야 limit이 실제 대상으로 채워진다.
        val candidateIds = queryFactory
            .select(catalog.id)
            .from(catalog)
            .join(koTranslation)
            .on(koTranslation.catalogId.eq(catalog.id), koTranslation.language.eq(DomainLocale.KO))
            .leftJoin(translation)
            .on(translation.catalogId.eq(catalog.id), translation.language.`in`(languages))
            .groupBy(catalog.id)
            .having(translation.id.count().lt(languages.size.toLong()))
            .orderBy(catalog.id.asc())
            .limit(limit.toLong())
            .fetch()

        if (candidateIds.isEmpty()) return emptyList()

        // 번역 원문은 KO 행의 제목과 주소다(R-4).
        val originals = queryFactory
            .select(koTranslation.catalogId, koTranslation.title, koTranslation.address)
            .from(koTranslation)
            .where(koTranslation.catalogId.`in`(candidateIds), koTranslation.language.eq(DomainLocale.KO))
            .fetch()
            .associate {
                requireNotNull(it.get(koTranslation.catalogId)) to
                    (requireNotNull(it.get(koTranslation.title)) to it.get(koTranslation.address))
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

            val (title, address) = originals[catalogId] ?: return@mapNotNull null
            UntranslatedCatalog(
                catalogId = catalogId,
                koreanTitle = title,
                koreanAddress = address,
                missingLanguages = missing,
            )
        }
    }
}
