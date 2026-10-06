package org.around.ieuri.tourism.adapter.persistence.query

import org.around.ieuri.tourism.adapter.persistence.mapper.toCatalogCandidate
import org.around.ieuri.tourism.adapter.persistence.mapper.toUnassignedSpot
import org.around.ieuri.tourism.adapter.persistence.table.SpotCatalogTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotCatalogTranslationTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotTable
import org.around.ieuri.tourism.application.catalog.required.CatalogCandidate
import org.around.ieuri.tourism.application.catalog.required.SpotCatalogQueryPort
import org.around.ieuri.tourism.application.catalog.required.UnassignedSpot
import org.around.ieuri.tourism.domain.catalog.CatalogStandardValues
import org.around.ieuri.tourism.domain.catalog.SpotTitleNormalizer
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.between
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.min
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.stereotype.Component

/** 카탈로그 배정의 읽기 포트 구현(SPEC-0019, CQRS 읽기). */
@Component
class SpotCatalogQueryAdapter : SpotCatalogQueryPort {

    /**
     * 배정 대상 — `catalog_id`가 null이고 좌표가 유효한 행. 미배정 조건이 곧 증분 처리다.
     * - (0,0)이거나 한국 밖인 좌표는 배정하지 않는다.
     */
    override fun findUnassigned(language: DomainLocale, afterSpotId: Long, limit: Int): List<UnassignedSpot> = SpotTable
        .select(
            SpotTable.id, SpotTable.language, SpotTable.title, SpotTable.address,
            SpotTable.thumbnailImageUrl, SpotTable.mapX, SpotTable.mapY,
            SpotTable.category, SpotTable.copyright,
        )
        .where {
            SpotTable.catalogId.isNull() and
                (SpotTable.language eq language.name) and
                (SpotTable.id greater afterSpotId) and
                SpotTable.mapX.between(CatalogStandardValues.KOREA_LON_RANGE.start, CatalogStandardValues.KOREA_LON_RANGE.endInclusive) and
                SpotTable.mapY.between(CatalogStandardValues.KOREA_LAT_RANGE.start, CatalogStandardValues.KOREA_LAT_RANGE.endInclusive)
        }
        .orderBy(SpotTable.id, SortOrder.ASC)
        .limit(limit)
        .map { it.toUnassignedSpot() }

    /**
     * KO 카탈로그의 제목 키 → 후보 맵. 배정 회차마다 한 번 읽어 메모리에서 대조한다.
     * - 정규화는 SQL이 아니라 도메인([SpotTitleNormalizer])이 한다.
     */
    override fun loadKoreanTitleIndex(): Map<String, List<CatalogCandidate>> = SpotCatalogTranslationTable
        .join(SpotCatalogTable, JoinType.INNER, SpotCatalogTranslationTable.catalogId, SpotCatalogTable.id)
        .select(
            SpotCatalogTable.id,
            SpotCatalogTable.mapX,
            SpotCatalogTable.mapY,
            SpotCatalogTable.thumbnailImageUrl,
            SpotCatalogTranslationTable.title,
        )
        .where { SpotCatalogTranslationTable.language eq DomainLocale.KO.name }
        .mapNotNull { row ->
            // 배정 쪽과 같은 함수여야 한다 — 갈리면 맵의 키와 조회 키가 어긋난다.
            val key = SpotTitleNormalizer.lookupKey(row[SpotCatalogTranslationTable.title]) ?: return@mapNotNull null
            key to row.toCatalogCandidate()
        }
        .groupBy({ it.first }) { it.second }

    /** 썸네일 URL → KO 카탈로그 id. KO spot이 정확히 하나인 URL만 담는다(`HAVING count = 1`). */
    override fun loadKoreanThumbnailIndex(): Map<String, Long> {
        val firstCatalogId = SpotTable.catalogId.min()
        val count = SpotTable.id.count()
        return SpotTable.select(SpotTable.thumbnailImageUrl, firstCatalogId)
            .where {
                (SpotTable.language eq DomainLocale.KO.name) and
                    SpotTable.catalogId.isNotNull() and
                    (SpotTable.thumbnailImageUrl neq "")
            }
            .groupBy(SpotTable.thumbnailImageUrl)
            .having { count eq 1L }
            .associate { it[SpotTable.thumbnailImageUrl] to requireNotNull(it[firstCatalogId]) }
    }
}
