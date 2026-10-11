package org.around.ieuri.tourism.adapter.persistence

import org.around.ieuri.common.persistence.stDistance
import org.around.ieuri.common.persistence.stIntersects
import org.around.ieuri.tourism.adapter.persistence.mapper.toCatalogCandidate
import org.around.ieuri.tourism.adapter.persistence.mapper.toCatalogOrigin
import org.around.ieuri.tourism.adapter.persistence.mapper.toCatalogStandardValues
import org.around.ieuri.tourism.adapter.persistence.mapper.toCatalogTranslation
import org.around.ieuri.tourism.adapter.persistence.mapper.toOriginFestivalPeriod
import org.around.ieuri.tourism.adapter.persistence.mapper.writeTo
import org.around.ieuri.tourism.adapter.persistence.table.DistrictTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotCatalogFestivalTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotCatalogTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotCatalogTranslationTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotFestivalTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotReactionTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotTable
import org.around.ieuri.tourism.application.catalog.required.SpotCatalogRepository
import org.around.ieuri.tourism.application.catalog.required.dto.CatalogLanguage
import org.around.ieuri.tourism.application.catalog.required.dto.VisibilityRefreshResult
import org.around.ieuri.tourism.domain.catalog.CatalogCandidate
import org.around.ieuri.tourism.domain.catalog.CatalogFestival
import org.around.ieuri.tourism.domain.catalog.CatalogMembership
import org.around.ieuri.tourism.domain.catalog.CatalogOrigin
import org.around.ieuri.tourism.domain.catalog.CatalogStandardValues
import org.around.ieuri.tourism.domain.catalog.CatalogTranslation
import org.around.ieuri.tourism.domain.catalog.OriginFestivalPeriod
import org.around.ieuri.tourism.domain.catalog.SpotCatalog
import org.around.ieuri.tourism.domain.catalog.SpotReaction
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
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.core.min
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.core.wrapAsExpression
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import org.springframework.stereotype.Repository

/** 카탈로그 애그리게이트 저장소 구현(SPEC-0019·SPEC-0029·SPEC-0030 R11). */
@Repository
class SpotCatalogPersistenceAdapter : SpotCatalogRepository {

    override fun findById(catalogId: Long): SpotCatalog? {
        val standard = SpotCatalogTable.selectAll().where { SpotCatalogTable.id eq catalogId }.singleOrNull()?.toCatalogStandardValues() ?: return null
        val translations = SpotCatalogTranslationTable.selectAll()
            .where { SpotCatalogTranslationTable.catalogId eq catalogId }
            .map { it.toCatalogTranslation() }
        return SpotCatalog(id = catalogId, standard = standard, translations = translations)
    }

    /** 카탈로그를 먼저 넣어 id를 받은 뒤 번역을 붙인다(FK 순서). */
    override fun insert(catalog: SpotCatalog): Long {
        val catalogId = SpotCatalogTable.insertReturning(listOf(SpotCatalogTable.id)) { catalog.standard.writeTo(it) }
            .single()[SpotCatalogTable.id]
        SpotCatalogTranslationTable.batchInsert(catalog.translations, shouldReturnGeneratedValues = false) {
            it.writeTo(this, catalogId, catalog.standard)
        }
        return catalogId
    }

    /** 같은 언어가 있으면 노출 여부와 사본 컬럼만 고친다 — 제목·주소는 덮지 않는다(SPEC-0019 R-4). */
    override fun saveTranslation(catalog: SpotCatalog, translation: CatalogTranslation) {
        val catalogId = requireNotNull(catalog.id) { "적재된 카탈로그에만 번역을 쓴다." }
        SpotCatalogTranslationTable.upsert(
            SpotCatalogTranslationTable.catalogId,
            SpotCatalogTranslationTable.language,
            onUpdate = {
                it[SpotCatalogTranslationTable.visible] = translation.visible
                it[SpotCatalogTranslationTable.mapX] = catalog.standard.mapX
                it[SpotCatalogTranslationTable.mapY] = catalog.standard.mapY
                it[SpotCatalogTranslationTable.category] = catalog.standard.category.code
                it[SpotCatalogTranslationTable.updatedAt] = CurrentTimestampWithTimeZone
            },
        ) { translation.writeTo(it, catalogId, catalog.standard) }
    }

    /** 빈 목록은 무동작이다 — 조건 없는 delete가 되면 번역이 전부 사라진다. */
    override fun deleteTranslations(catalogId: Long, languages: List<DomainLocale>) {
        if (languages.isEmpty()) return
        SpotCatalogTranslationTable.deleteWhere {
            (SpotCatalogTranslationTable.catalogId eq catalogId) and (SpotCatalogTranslationTable.language inList languages.map { it.name })
        }
    }

    override fun saveMembership(membership: CatalogMembership) {
        when (membership) {
            is CatalogMembership.Join ->
                SpotTable.update({ (SpotTable.id eq membership.spotId) and SpotTable.catalogId.isNull() }) { it[SpotTable.catalogId] = membership.catalogId }

            is CatalogMembership.Move -> {
                if (membership.spotIds.isEmpty()) return
                SpotTable.update({ SpotTable.id inList membership.spotIds }) { it[SpotTable.catalogId] = membership.catalogId }
            }
        }
    }

    override fun findOriginFestivalPeriods(catalogId: Long): List<OriginFestivalPeriod> = SpotTable
        .join(SpotFestivalTable, JoinType.INNER, SpotTable.id, SpotFestivalTable.spotId)
        .select(SpotTable.id, SpotTable.language, SpotFestivalTable.eventStartDate, SpotFestivalTable.eventEndDate)
        .where { SpotTable.catalogId eq catalogId }
        .map { it.toOriginFestivalPeriod() }

    override fun saveFestival(festival: CatalogFestival) {
        val catalogId = festival.catalogId
        val period = festival.period
        if (period == null) {
            SpotCatalogFestivalTable.deleteWhere { SpotCatalogFestivalTable.catalogId eq catalogId }
            return
        }
        SpotCatalogFestivalTable.upsert(
            SpotCatalogFestivalTable.catalogId,
            onUpdate = {
                it[SpotCatalogFestivalTable.eventStartDate] = period.start
                it[SpotCatalogFestivalTable.eventEndDate] = period.end
                it[SpotCatalogFestivalTable.updatedAt] = CurrentTimestampWithTimeZone
            },
        ) {
            it[SpotCatalogFestivalTable.catalogId] = catalogId
            it[SpotCatalogFestivalTable.eventStartDate] = period.start
            it[SpotCatalogFestivalTable.eventEndDate] = period.end
        }
    }

    /** 반응이 실제로 바뀔 때만 갱신한다 — 같은 반응을 다시 누르면 `updated_at`(좋아요 목록 정렬 키)이 그대로다. */
    override fun saveReaction(reaction: SpotReaction) {
        SpotReactionTable.upsert(
            SpotReactionTable.memberId,
            SpotReactionTable.catalogId,
            SpotReactionTable.language,
            onUpdate = {
                it[SpotReactionTable.reaction] = reaction.reaction.name
                it[SpotReactionTable.updatedAt] = CurrentTimestampWithTimeZone
            },
            where = { SpotReactionTable.reaction neq reaction.reaction.name },
        ) { reaction.writeTo(it) }
    }

    override fun deleteReactionsByMember(memberId: Long): Int = SpotReactionTable.deleteWhere { SpotReactionTable.memberId eq memberId }

    /** 상태가 실제로 바뀐 행만 갱신하고, 반영 후 노출 중인 것 전부를 돌려준다. */
    override fun refreshTranslationVisibility(): VisibilityRefreshResult {
        // 그 언어의 원본(spot)이 이 카탈로그에 붙어 있는가 = 노출 여부.
        val originExists = exists(
            SpotTable.select(SpotTable.id).where {
                (SpotTable.catalogId eq SpotCatalogTranslationTable.catalogId) and (SpotTable.language eq SpotCatalogTranslationTable.language)
            },
        )
        val hidden = SpotCatalogTranslationTable.update({ (SpotCatalogTranslationTable.visible eq true) and not(originExists) }) {
            it[SpotCatalogTranslationTable.visible] = false
        }
        val shown = SpotCatalogTranslationTable.update({ (SpotCatalogTranslationTable.visible eq false) and originExists }) {
            it[SpotCatalogTranslationTable.visible] = true
        }
        val visible = SpotCatalogTranslationTable
            .select(SpotCatalogTranslationTable.catalogId, SpotCatalogTranslationTable.language)
            .where { SpotCatalogTranslationTable.visible eq true }
            .map { CatalogLanguage(it[SpotCatalogTranslationTable.catalogId], DomainLocale.valueOf(it[SpotCatalogTranslationTable.language])) }
        return VisibilityRefreshResult(hiddenCount = hidden, shownCount = shown, visible = visible)
    }

    /**
     * 좌표를 품은 시군구를 채운다. 바다·경계 밖 좌표는 매칭되지 않아 다음 실행에 다시 시도된다.
     * - 경계에 걸친 좌표는 맞닿은 시군구 중 하나를 쓴다. `location`을 `::geometry`로 캐스팅하지 않는다 — GiST 인덱스가 죽는다.
     */
    override fun assignUnassignedDistricts(): Int {
        val containing = DistrictTable.select(DistrictTable.code)
            .where { stIntersects(DistrictTable.boundary, SpotCatalogTable.location) }
            .limit(1)
        return SpotCatalogTable.update({ SpotCatalogTable.districtCode.isNull() and exists(containing) }) {
            it[SpotCatalogTable.districtCode] = wrapAsExpression<String>(containing)
            it[SpotCatalogTable.updatedAt] = CurrentTimestampWithTimeZone
        }
    }

    override fun countDistrictUnassigned(): Int = SpotCatalogTable.selectAll().where { SpotCatalogTable.districtCode.isNull() }.count().toInt()

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
        val origin = SpotTable.alias("origin")
        val other = SpotTable.alias("other_origin")
        val farthestApart = stDistance(origin[SpotTable.location], other[SpotTable.location]).max()
        return origin.join(other, JoinType.INNER, additionalConstraint = { (other[SpotTable.catalogId] eq origin[SpotTable.catalogId]) and (other[SpotTable.id] greater origin[SpotTable.id]) })
            .select(origin[SpotTable.catalogId])
            .where { origin[SpotTable.catalogId] inSubQuery crowded }
            .groupBy(origin[SpotTable.catalogId])
            .having { farthestApart greaterEq minDistanceMeters }
            .orderBy(origin[SpotTable.catalogId], SortOrder.ASC)
            .map { requireNotNull(it[origin[SpotTable.catalogId]]) }
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
