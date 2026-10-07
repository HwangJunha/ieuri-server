package org.around.ieuri.tourism.adapter.persistence.query

import org.around.ieuri.common.pagination.CursorEdge
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.CursorPageInfo
import org.around.ieuri.tourism.adapter.persistence.mapper.toLikedSpotView
import org.around.ieuri.tourism.adapter.persistence.mapper.toSpotBasics
import org.around.ieuri.tourism.adapter.persistence.mapper.toSpotIdentity
import org.around.ieuri.tourism.adapter.persistence.table.SpotCatalogTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotCatalogTranslationTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotReactionTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotTable
import org.around.ieuri.tourism.application.catalog.provided.dto.LikedSpotView
import org.around.ieuri.tourism.application.catalog.required.LikedSpotsSearch
import org.around.ieuri.tourism.application.catalog.required.SpotBasics
import org.around.ieuri.tourism.application.catalog.required.SpotIdentity
import org.around.ieuri.tourism.application.catalog.required.SpotQueryPort
import org.around.ieuri.tourism.domain.catalog.ReactionType
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * 관광지 읽기 포트 구현(CQRS 읽기). 애그리거트를 우회해 필요한 값만 프로젝션한다.
 * - 반경 조회는 [SpotSpatialQueryAdapter]가 따로 맡는다.
 */
@Component
class SpotQueryAdapter : SpotQueryPort {

    /** 노출 중인 번역만 조인한 카탈로그. 카드 기본값(SpotBasics)을 읽는 모양이다. */
    private val visibleCatalog = SpotCatalogTable.join(
        SpotCatalogTranslationTable,
        JoinType.INNER,
        additionalConstraint = { (SpotCatalogTranslationTable.catalogId eq SpotCatalogTable.id) and (SpotCatalogTranslationTable.visible eq true) },
    )

    private val basicsColumns = listOf(
        SpotCatalogTranslationTable.title,
        SpotCatalogTranslationTable.address,
        SpotCatalogTable.thumbnailImageUrl,
        SpotCatalogTable.category,
        SpotCatalogTable.copyright,
        SpotCatalogTable.mapX,
        SpotCatalogTable.mapY,
    )

    /**
     * 그 회원이 [language]에서 이미 반응한 카탈로그 id(SPEC-0029 R-13). 반경 조회의 제외 집합이다.
     * - `spot`을 경유하지 않는다 — 조인하면 그 언어의 spot 행이 없는 카탈로그가 빠진다.
     */
    override fun findReactedSpotIds(memberId: Long, language: DomainLocale): Set<Long> = SpotReactionTable.select(SpotReactionTable.catalogId)
        .where { (SpotReactionTable.memberId eq memberId) and (SpotReactionTable.language eq language.name) }
        .mapTo(mutableSetOf()) { it[SpotReactionTable.catalogId] }

    override fun findReactedSpotIdsByReaction(memberId: Long, language: DomainLocale): Map<ReactionType, Set<Long>> = SpotReactionTable
        .select(SpotReactionTable.catalogId, SpotReactionTable.reaction)
        .where { (SpotReactionTable.memberId eq memberId) and (SpotReactionTable.language eq language.name) }
        .groupBy({ ReactionType.valueOf(it[SpotReactionTable.reaction]) }, { it[SpotReactionTable.catalogId] })
        .mapValues { (_, ids) -> ids.toSet() }

    /** 활성 언어 안의 LIKE 반응 수(SPEC-0029 AC-13.5). 다른 언어의 좋아요는 합산되지 않는다. */
    override fun countLikes(catalogId: Long, language: DomainLocale): Long {
        val count = SpotReactionTable.id.count()
        return SpotReactionTable.select(count)
            .where {
                (SpotReactionTable.catalogId eq catalogId) and
                    (SpotReactionTable.language eq language.name) and
                    (SpotReactionTable.reaction eq ReactionType.LIKE.name)
            }
            .single()[count]
    }

    /** 이 회원이 이 언어에서 남긴 반응. 없으면 null이다 — 다른 언어로 누른 반응도 null이다(AC-13.2). */
    override fun findReaction(catalogId: Long, memberId: Long, language: DomainLocale): ReactionType? = SpotReactionTable
        .select(SpotReactionTable.reaction)
        .where {
            (SpotReactionTable.catalogId eq catalogId) and
                (SpotReactionTable.memberId eq memberId) and
                (SpotReactionTable.language eq language.name)
        }
        .limit(1).singleOrNull()
        ?.let { ReactionType.valueOf(it[SpotReactionTable.reaction]) }

    /**
     * (카탈로그 id, 언어) → upstream 식별 정보. 상세 조회가 캐시·DB를 모두 놓쳤을 때만 쓴다.
     * - [language] 원본만 본다. 없으면 null이며 호출자가 `SPOT_NOT_FOUND_IN_LOCALE`로 옮긴다(SPEC-0029 R-10).
     * - 좌표는 `spot_catalog`에서 읽는다(SPEC-0027).
     */
    override fun findIdentity(catalogId: Long, language: DomainLocale): SpotIdentity? = SpotTable
        .join(SpotCatalogTable, JoinType.INNER, SpotTable.catalogId, SpotCatalogTable.id)
        .select(SpotTable.id, SpotTable.contentId, SpotCatalogTable.mapX, SpotCatalogTable.mapY)
        .where { (SpotTable.catalogId eq catalogId) and (SpotTable.language eq language.name) }
        .limit(1).singleOrNull()
        ?.toSpotIdentity()

    /** 카탈로그 존재 여부(언어 무관). 두 오류 코드를 가르는 데만 쓴다(AC-12.3). */
    override fun existsCatalog(catalogId: Long): Boolean = SpotCatalogTable.select(SpotCatalogTable.id)
        .where { SpotCatalogTable.id eq catalogId }
        .limit(1).any()

    /**
     * 상세 본문이 없을 때 응답을 구성할 카탈로그 값. 인자는 카탈로그 id다.
     * - 제목·주소는 [language] 번역만 본다(폴백 없음, SPEC-0029 R-10). 번역이나 카탈로그가 없으면 null이다.
     * - 두 경우를 구분하려면 [existsCatalog]를 함께 본다.
     */
    override fun findSpotBasics(catalogId: Long, language: DomainLocale): SpotBasics? = visibleCatalog
        .select(basicsColumns)
        .where { (SpotCatalogTable.id eq catalogId) and (SpotCatalogTranslationTable.language eq language.name) }
        .limit(1).singleOrNull()
        ?.toSpotBasics()

    /**
     * 여러 카탈로그의 [findSpotBasics]를 한 번에 읽는다.
     * - [language]에서 해소되지 않는 카탈로그는 결과 키에서 빠진다(SPEC-0029 AC-24.3).
     */
    override fun findSpotBasicsIn(catalogIds: List<Long>, language: DomainLocale): Map<Long, SpotBasics> {
        if (catalogIds.isEmpty()) return emptyMap()

        return visibleCatalog
            .select(basicsColumns + SpotCatalogTable.id)
            .where { (SpotCatalogTable.id inList catalogIds) and (SpotCatalogTranslationTable.language eq language.name) }
            .associate { it[SpotCatalogTable.id] to it.toSpotBasics() }
    }

    override fun findLikedSpots(query: LikedSpotsSearch): CursorPage<LikedSpotView> {
        val cursor = query.after?.let { LikedSpotCursor.decode(it) }

        var condition: Op<Boolean> = (SpotReactionTable.memberId eq query.memberId) and
            (SpotReactionTable.reaction eq ReactionType.LIKE.name) and
            // 반응의 언어(AC-14.1/14.2). 빠지면 다른 언어에서 누른 좋아요가 섞인다.
            (SpotReactionTable.language eq query.language.name)
        if (!query.categories.isUnfiltered) {
            condition = condition and (SpotCatalogTable.category inList query.categories.codes())
        }
        // 정렬이 (updated_at DESC, catalog_id DESC)이므로 다음 페이지는 그 튜플보다 "작은" 행이다.
        cursor?.let {
            val updatedAt = OffsetDateTime.ofInstant(it.updatedAt, ZoneOffset.UTC)
            condition = condition and (
                (SpotReactionTable.updatedAt less updatedAt) or
                    ((SpotReactionTable.updatedAt eq updatedAt) and (SpotReactionTable.catalogId less it.catalogId))
                )
        }

        val rows = SpotReactionTable
            // 제목·주소는 번역에서 온다(R-4·R-9). 번역이 없는 언어의 항목은 빠진다.
            .join(
                SpotCatalogTranslationTable,
                JoinType.INNER,
                additionalConstraint = {
                    (SpotCatalogTranslationTable.catalogId eq SpotReactionTable.catalogId) and
                        (SpotCatalogTranslationTable.language eq query.language.name)
                },
            )
            // 좌표·썸네일·분류는 카탈로그의 표준값이다.
            // - 여기만 `visible`을 걸지 않는다 — 원본이 사라진 장소의 좋아요도 보인다.
            .join(SpotCatalogTable, JoinType.INNER, SpotReactionTable.catalogId, SpotCatalogTable.id)
            .select(
                SpotCatalogTable.id,
                SpotCatalogTranslationTable.title,
                SpotCatalogTranslationTable.address,
                SpotCatalogTable.thumbnailImageUrl,
                SpotCatalogTable.category,
                SpotCatalogTable.copyright,
                SpotReactionTable.updatedAt,
            )
            .where { condition }
            .orderBy(SpotReactionTable.updatedAt to SortOrder.DESC, SpotReactionTable.catalogId to SortOrder.DESC)
            .limit(query.first + 1)
            .map { it.toLikedSpotView() }

        val hasNextPage = rows.size > query.first
        val pageRows = if (hasNextPage) rows.take(query.first) else rows
        val edges = pageRows.map {
            CursorEdge(cursor = LikedSpotCursor(it.likedAt, it.catalogId).encode(), node = it)
        }

        return CursorPage(
            edges = edges,
            pageInfo = CursorPageInfo(
                hasNextPage = hasNextPage,
                hasPreviousPage = cursor != null,
                startCursor = edges.firstOrNull()?.cursor,
                endCursor = edges.lastOrNull()?.cursor,
            ),
            // 좋아요 목록은 총 개수를 노출하지 않는다(null).
            totalCount = null,
        )
    }
}
