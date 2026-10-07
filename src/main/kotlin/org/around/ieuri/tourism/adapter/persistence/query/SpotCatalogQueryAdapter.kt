package org.around.ieuri.tourism.adapter.persistence.query

import org.around.ieuri.common.persistence.stDistance
import org.around.ieuri.tourism.adapter.persistence.mapper.toCatalogCandidate
import org.around.ieuri.tourism.adapter.persistence.mapper.toCatalogOrigin
import org.around.ieuri.tourism.adapter.persistence.table.SpotCatalogTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotCatalogTranslationTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotTable
import org.around.ieuri.tourism.application.catalog.required.SpotCatalogQueryPort
import org.around.ieuri.tourism.domain.catalog.CatalogCandidate
import org.around.ieuri.tourism.domain.catalog.CatalogOrigin
import org.around.ieuri.tourism.domain.catalog.CatalogStandardValues
import org.around.ieuri.tourism.domain.catalog.SpotCatalog
import org.around.ieuri.tourism.domain.catalog.SpotTitleNormalizer
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.between
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.core.min
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.stereotype.Component

/** 카탈로그 정비가 읽는 것(SPEC-0019·SPEC-0029). */
@Component
class SpotCatalogQueryAdapter : SpotCatalogQueryPort {

    /** 미배정 조건이 곧 증분 처리다. 좌표가 한국 밖인 원본은 배정하지 않는다. */
    override fun findUnassigned(language: DomainLocale, afterSpotId: Long, limit: Int): List<CatalogOrigin> = SpotTable
        .select(ORIGIN_COLUMNS)
        .where { SpotTable.catalogId.isNull() and (SpotTable.language eq language.name) and (SpotTable.id greater afterSpotId) and inKorea() }
        .orderBy(SpotTable.id, SortOrder.ASC)
        .limit(limit)
        .map { it.toCatalogOrigin() }

    /** 정규화는 SQL이 아니라 도메인([SpotTitleNormalizer])이 한다 — 배정 쪽 키와 같은 함수여야 한다. */
    override fun loadKoreanTitleIndex(): Map<String, List<CatalogCandidate>> = SpotCatalogTranslationTable
        .join(SpotCatalogTable, JoinType.INNER, SpotCatalogTranslationTable.catalogId, SpotCatalogTable.id)
        .select(SpotCatalogTable.id, SpotCatalogTable.mapX, SpotCatalogTable.mapY, SpotCatalogTable.thumbnailImageUrl, SpotCatalogTranslationTable.title)
        .where { SpotCatalogTranslationTable.language eq DomainLocale.KO.name }
        .mapNotNull { row ->
            val key = SpotTitleNormalizer.lookupKey(row[SpotCatalogTranslationTable.title]) ?: return@mapNotNull null
            key to row.toCatalogCandidate()
        }
        .groupBy({ it.first }) { it.second }

    /** KO 원본이 정확히 하나인 URL만 담는다(`HAVING count = 1`). */
    override fun loadKoreanThumbnailIndex(): Map<String, Long> {
        val firstCatalogId = SpotTable.catalogId.min()
        return SpotTable.select(SpotTable.thumbnailImageUrl, firstCatalogId)
            .where { (SpotTable.language eq DomainLocale.KO.name) and SpotTable.catalogId.isNotNull() and (SpotTable.thumbnailImageUrl neq "") }
            .groupBy(SpotTable.thumbnailImageUrl)
            .having { SpotTable.id.count() eq 1L }
            .associate { it[SpotTable.thumbnailImageUrl] to requireNotNull(it[firstCatalogId]) }
    }

    /**
     * 원본 수로 먼저 거른 뒤 그 카탈로그에 한해 원본끼리 짝지어 최대 거리를 잰다.
     * - 순서가 뒤집히면 자기 조인이 원본 수의 제곱으로 커진다.
     */
    override fun findSplitCandidates(minOriginCount: Int, minDistanceMeters: Double): List<Long> {
        val crowded = SpotTable.select(SpotTable.catalogId)
            .where { SpotTable.catalogId.isNotNull() }
            .groupBy(SpotTable.catalogId)
            .having { SpotTable.id.count() greater minOriginCount.toLong() }
        val a = SpotTable.alias("a")
        val b = SpotTable.alias("b")
        val farthest = stDistance(a[SpotTable.location], b[SpotTable.location]).max()
        return a.join(b, JoinType.INNER, additionalConstraint = { (b[SpotTable.catalogId] eq a[SpotTable.catalogId]) and (b[SpotTable.id] greater a[SpotTable.id]) })
            .select(a[SpotTable.catalogId])
            .where { a[SpotTable.catalogId] inSubQuery crowded }
            .groupBy(a[SpotTable.catalogId])
            .having { farthest greaterEq minDistanceMeters }
            .orderBy(a[SpotTable.catalogId], SortOrder.ASC)
            .map { requireNotNull(it[a[SpotTable.catalogId]]) }
    }

    /** 대표 선정과 군집 결과가 실행마다 같도록 언어 순서(KO→EN→JA→ZH), 같은 언어면 id 순으로 돌려준다. */
    override fun findOrigins(catalogId: Long): List<CatalogOrigin> = SpotTable
        .select(ORIGIN_COLUMNS)
        .where { (SpotTable.catalogId eq catalogId) and inKorea() }
        .map { it.toCatalogOrigin() }
        .sortedWith(compareBy<CatalogOrigin> { SpotCatalog.LANGUAGE_PRIORITY.indexOf(it.language) }.thenBy { it.spotId })

    /** 좌표 범위가 배정과 같다 — 범위 밖 한 건이 분할 전체를 실패시키지 않게 한다. */
    private fun inKorea(): Op<Boolean> = SpotTable.mapX.between(CatalogStandardValues.KOREA_LON_RANGE.start, CatalogStandardValues.KOREA_LON_RANGE.endInclusive) and
        SpotTable.mapY.between(CatalogStandardValues.KOREA_LAT_RANGE.start, CatalogStandardValues.KOREA_LAT_RANGE.endInclusive)

    private companion object {
        val ORIGIN_COLUMNS = listOf(
            SpotTable.id, SpotTable.language, SpotTable.title, SpotTable.address, SpotTable.thumbnailImageUrl,
            SpotTable.mapX, SpotTable.mapY, SpotTable.category, SpotTable.copyright,
        )
    }
}
