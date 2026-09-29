package org.around.ieuri.tourism.infrastructure.adapter.out.query

import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.common.pagination.CursorEdge
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.CursorPageInfo
import org.around.ieuri.tourism.application.exception.InvalidCursorException
import org.around.ieuri.tourism.application.port.out.query.LikedSpotsSearch
import org.around.ieuri.tourism.application.port.out.query.SpotBasics
import org.around.ieuri.tourism.application.port.out.query.SpotIdentity
import org.around.ieuri.tourism.application.port.out.query.SpotQueryPort
import org.around.ieuri.tourism.application.query.LikedSpotView
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.LikedSpotCursor
import org.around.ieuri.tourism.domain.vo.ReactionType
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotCatalogEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotCatalogTranslationEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotReactionEntity
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 관광지 읽기 포트 구현(CQRS 읽기). 애그리거트를 우회해 필요한 값만 프로젝션한다.
 * - 반경 조회는 [SpotSpatialQueryAdapter]가 따로 맡는다.
 */
@Component
class SpotQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : SpotQueryPort {

    private val spot = QSpotEntity.spotEntity
    private val reaction = QSpotReactionEntity.spotReactionEntity
    private val catalog = QSpotCatalogEntity.spotCatalogEntity
    private val translation = QSpotCatalogTranslationEntity.spotCatalogTranslationEntity

    /**
     * 그 회원이 [language]에서 이미 반응한 카탈로그 id(SPEC-0029 R-13). 반경 조회의 제외 집합이다.
     * - `spot`을 경유하지 않는다 — 조인하면 그 언어의 spot 행이 없는 카탈로그가 빠진다.
     */
    override fun findReactedSpotIds(memberId: Long, language: DomainLocale): Set<Long> = queryFactory
        .select(reaction.catalogId)
        .from(reaction)
        .where(reaction.memberId.eq(memberId), reaction.language.eq(language))
        .fetch()
        .toSet()

    override fun findReactedSpotIdsByReaction(memberId: Long, language: DomainLocale): Map<ReactionType, Set<Long>> = queryFactory
        .select(reaction.catalogId, reaction.reaction)
        .from(reaction)
        .where(reaction.memberId.eq(memberId), reaction.language.eq(language))
        .fetch()
        .groupBy(
            { requireNotNull(it.get(reaction.reaction)) },
            { requireNotNull(it.get(reaction.catalogId)) },
        )
        .mapValues { (_, ids) -> ids.toSet() }

    /** 활성 언어 안의 LIKE 반응 수(SPEC-0029 AC-13.5). 다른 언어의 좋아요는 합산되지 않는다. */
    override fun countLikes(catalogId: Long, language: DomainLocale): Long = queryFactory
        .select(reaction.count())
        .from(reaction)
        .where(
            reaction.catalogId.eq(catalogId),
            reaction.language.eq(language),
            reaction.reaction.eq(ReactionType.LIKE),
        )
        .fetchOne() ?: 0L

    /** 이 회원이 이 언어에서 남긴 반응. 없으면 null이다 — 다른 언어로 누른 반응도 null이다(AC-13.2). */
    @Transactional(readOnly = true)
    override fun findReaction(catalogId: Long, memberId: Long, language: DomainLocale): ReactionType? = queryFactory
        .select(reaction.reaction)
        .from(reaction)
        .where(
            reaction.catalogId.eq(catalogId),
            reaction.memberId.eq(memberId),
            reaction.language.eq(language),
        )
        .fetchFirst()

    /**
     * (카탈로그 id, 언어) → upstream 식별 정보. 상세 조회가 캐시·DB를 모두 놓쳤을 때만 쓴다.
     * - [language] 원본만 본다. 없으면 null이며 호출자가 `SPOT_NOT_FOUND_IN_LOCALE`로 옮긴다(SPEC-0029 R-10).
     * - 좌표는 `spot_catalog`에서 읽는다(SPEC-0027).
     * - nullable `spot.catalog_id`에 건 INNER 조인이다 — where의 `catalogId` 조건을 풀면 배정 안 된 spot이 null이 된다.
     */
    override fun findIdentity(catalogId: Long, language: DomainLocale): SpotIdentity? = queryFactory
        .select(spot.id, spot.contentId, catalog.mapX, catalog.mapY)
        .from(spot)
        .join(catalog).on(catalog.id.eq(spot.catalogId))
        .where(spot.catalogId.eq(catalogId), spot.language.eq(language))
        .fetchFirst()
        ?.let {
            SpotIdentity(
                spotId = requireNotNull(it.get(spot.id)),
                contentId = requireNotNull(it.get(spot.contentId)),
                mapX = requireNotNull(it.get(catalog.mapX)),
                mapY = requireNotNull(it.get(catalog.mapY)),
            )
        }

    /** 카탈로그 존재 여부(언어 무관). 두 오류 코드를 가르는 데만 쓴다(AC-12.3). */
    override fun existsCatalog(catalogId: Long): Boolean = queryFactory
        .selectOne()
        .from(catalog)
        .where(catalog.id.eq(catalogId))
        .fetchFirst() != null

    /**
     * 상세 본문이 없을 때 응답을 구성할 카탈로그 값. 인자는 카탈로그 id다.
     * - 제목·주소는 [language] 번역만 본다(폴백 없음, SPEC-0029 R-10). 번역이나 카탈로그가 없으면 null이다.
     * - 두 경우를 구분하려면 [existsCatalog]를 함께 본다.
     */
    override fun findSpotBasics(catalogId: Long, language: DomainLocale): SpotBasics? = queryFactory
        .select(
            translation.title,
            translation.address,
            catalog.thumbnailImageUrl,
            catalog.category,
            catalog.copyright,
            catalog.mapX,
            catalog.mapY,
        )
        .from(catalog)
        .join(translation).on(translation.catalogId.eq(catalog.id), translation.visible.isTrue)
        .where(catalog.id.eq(catalogId), translation.language.eq(language))
        .fetchFirst()
        ?.let {
            SpotBasics(
                title = requireNotNull(it.get(translation.title)),
                address = it.get(translation.address),
                thumbnailImageUrl = requireNotNull(it.get(catalog.thumbnailImageUrl)),
                category = SpotCategory(requireNotNull(it.get(catalog.category))),
                copyright = requireNotNull(it.get(catalog.copyright)),
                longitude = requireNotNull(it.get(catalog.mapX)),
                latitude = requireNotNull(it.get(catalog.mapY)),
            )
        }

    /**
     * 여러 카탈로그의 [findSpotBasics]를 한 번에 읽는다.
     * - [language]에서 해소되지 않는 카탈로그는 결과 키에서 빠진다(SPEC-0029 AC-24.3).
     */
    @Transactional(readOnly = true)
    override fun findSpotBasicsIn(catalogIds: List<Long>, language: DomainLocale): Map<Long, SpotBasics> {
        if (catalogIds.isEmpty()) return emptyMap()

        return queryFactory
            .select(
                catalog.id,
                translation.title,
                translation.address,
                catalog.thumbnailImageUrl,
                catalog.category,
                catalog.copyright,
                catalog.mapX,
                catalog.mapY,
            )
            .from(catalog)
            .join(translation).on(translation.catalogId.eq(catalog.id), translation.visible.isTrue)
            .where(catalog.id.`in`(catalogIds), translation.language.eq(language))
            .fetch()
            .associate { row ->
                requireNotNull(row.get(catalog.id)) to SpotBasics(
                    title = requireNotNull(row.get(translation.title)),
                    address = row.get(translation.address),
                    thumbnailImageUrl = requireNotNull(row.get(catalog.thumbnailImageUrl)),
                    category = SpotCategory(requireNotNull(row.get(catalog.category))),
                    copyright = requireNotNull(row.get(catalog.copyright)),
                    longitude = requireNotNull(row.get(catalog.mapX)),
                    latitude = requireNotNull(row.get(catalog.mapY)),
                )
            }
    }

    override fun findLikedSpots(query: LikedSpotsSearch): CursorPage<LikedSpotView> {
        val cursor = query.after?.let {
            // 구 커서(v2 접두사 없음)는 형식 단계에서 거부된다(SPEC-0019 §4.3).
            try {
                LikedSpotCursor.decode(it)
            } catch (e: IllegalArgumentException) {
                throw InvalidCursorException(cause = e)
            }
        }

        val rows = queryFactory
            .select(
                catalog.id,
                translation.title,
                translation.address,
                catalog.thumbnailImageUrl,
                catalog.category,
                catalog.copyright,
                reaction.updatedAt,
            )
            .from(reaction)
            // 제목·주소는 번역에서 온다(R-4·R-9). 번역이 없는 언어의 항목은 빠진다.
            .join(translation).on(
                translation.catalogId.eq(reaction.catalogId),
                translation.language.eq(query.language),
            )
            // 좌표·썸네일·분류는 카탈로그의 표준값이다.
            // - 여기만 `visible`을 걸지 않는다 — 원본이 사라진 장소의 좋아요도 보인다.
            .join(catalog).on(catalog.id.eq(reaction.catalogId))
            .where(
                reaction.memberId.eq(query.memberId),
                reaction.reaction.eq(ReactionType.LIKE),
                // 반응의 언어(AC-14.1/14.2). 빠지면 다른 언어에서 누른 좋아요가 섞인다.
                reaction.language.eq(query.language),
                query.categories.takeIf { !it.isUnfiltered }?.let { catalog.category.`in`(it.codes()) },
                // 정렬이 (updated_at DESC, catalog_id DESC)이므로 다음 페이지는 그 튜플보다 "작은" 행이다.
                // 총계에는 붙이지 않는다.
                cursor?.let {
                    reaction.updatedAt.lt(it.updatedAt)
                        .or(reaction.updatedAt.eq(it.updatedAt).and(reaction.catalogId.lt(it.catalogId)))
                },
            )
            .orderBy(reaction.updatedAt.desc(), reaction.catalogId.desc())
            .limit((query.first + 1).toLong())
            .fetch()
            .map {
                val copyright = requireNotNull(it.get(catalog.copyright))
                val catalogId = requireNotNull(it.get(catalog.id))
                LikedSpotView(
                    // 클라이언트에 나가는 id는 카탈로그 id다.
                    id = catalogId,
                    title = requireNotNull(it.get(translation.title)),
                    thumbnailImageUrl = requireNotNull(it.get(catalog.thumbnailImageUrl)),
                    address = it.get(translation.address),
                    category = SpotCategory(requireNotNull(it.get(catalog.category))),
                    copyright = copyright,
                    // copyright에서 도출되는 값이라 적재하지 않는다.
                    attributionText = copyright.attributionText,
                    likedAt = requireNotNull(it.get(reaction.updatedAt)),
                    catalogId = catalogId,
                )
            }

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
