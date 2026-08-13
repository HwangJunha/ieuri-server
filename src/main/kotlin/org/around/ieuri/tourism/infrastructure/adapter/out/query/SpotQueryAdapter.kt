package org.around.ieuri.tourism.infrastructure.adapter.out.query

import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.common.pagination.CursorEdge
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.CursorPageInfo
import org.around.ieuri.tourism.application.port.out.query.LikedSpotsSearch
import org.around.ieuri.tourism.application.port.out.query.NearbySpotSearch
import org.around.ieuri.tourism.application.port.out.query.SpotBasics
import org.around.ieuri.tourism.application.port.out.query.SpotIdentity
import org.around.ieuri.tourism.application.port.out.query.SpotQueryPort
import org.around.ieuri.tourism.application.port.out.query.SpotSearchResult
import org.around.ieuri.tourism.application.query.LikedSpotView
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.ReactionType
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotReactionEntity
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 관광지 읽기 포트 구현(CQRS 읽기). 애그리거트를 우회해 필요한 값만 프로젝션한다.
 * 읽기 소스는 두 테이블이다: spot(관광지 본체) + spot_reaction(반응 필터·좋아요 집계).
 *
 * 반경 조회만 PostGIS가 필요해 [SpotSpatialQueryAdapter]에 위임한다 — 포트는 하나로 남고
 * 애플리케이션 계층은 이 분할을 알지 못한다.
 */
@Component
class SpotQueryAdapter(
    private val queryFactory: JPAQueryFactory,
    private val spatialQueryAdapter: SpotSpatialQueryAdapter,
) : SpotQueryPort {

    private val spot = QSpotEntity.spotEntity
    private val reaction = QSpotReactionEntity.spotReactionEntity

    @Transactional(readOnly = true)
    override fun searchNearby(query: NearbySpotSearch): List<SpotSearchResult> =
        spatialQueryAdapter.searchNearby(query)

    @Transactional(readOnly = true)
    override fun countNearby(query: NearbySpotSearch): Int =
        spatialQueryAdapter.countNearby(query)

    @Transactional(readOnly = true)
    override fun findSpotIdsWithin(
        longitude: Double,
        latitude: Double,
        radiusMeters: Int,
        limit: Int,
        categoryCode: String?,
    ): List<Long> =
        spatialQueryAdapter.findIdsWithin(longitude, latitude, radiusMeters, limit, categoryCode)

    /** 그 회원이 해당 언어에서 이미 반응한 관광지 id. 언어는 `spot` 조인으로 판정한다. */
    override fun findReactedSpotIds(memberId: Long, language: DomainLocale): Set<Long> =
        queryFactory
            .select(reaction.spotId)
            .from(reaction)
            .join(spot).on(spot.id.eq(reaction.spotId))
            .where(
                reaction.memberId.eq(memberId),
                spot.language.eq(language),
            )
            .fetch()
            .toSet()

    /** LIKE 반응 수. `spotId`가 이미 언어별이라 언어 조건은 두지 않는다. */
    override fun countLikes(spotId: Long): Long =
        queryFactory
            .select(reaction.count())
            .from(reaction)
            .where(
                reaction.spotId.eq(spotId),
                reaction.reaction.eq(ReactionType.LIKE),
            )
            .fetchOne() ?: 0L

    /** `spot.id` → upstream 식별 정보. 상세 조회가 캐시·DB를 모두 놓쳤을 때만 쓴다. */
    override fun findIdentity(spotId: Long): SpotIdentity? =
        queryFactory
            .select(spot.contentId, spot.language)
            .from(spot)
            .where(spot.id.eq(spotId))
            .fetchFirst()
            ?.let {
                SpotIdentity(
                    contentId = requireNotNull(it.get(spot.contentId)),
                    language = requireNotNull(it.get(spot.language)),
                )
            }

    /** 상세 본문이 부재할 때 응답을 구성할 `spot` 행의 값. */
    override fun findSpotBasics(spotId: Long): SpotBasics? =
        queryFactory
            .select(
                spot.title, spot.address, spot.thumbnailImageUrl,
                spot.category, spot.copyright, spot.mapX, spot.mapY,
            )
            .from(spot)
            .where(spot.id.eq(spotId))
            .fetchFirst()
            ?.let {
                SpotBasics(
                    title = requireNotNull(it.get(spot.title)),
                    address = it.get(spot.address),
                    thumbnailImageUrl = requireNotNull(it.get(spot.thumbnailImageUrl)),
                    category = requireNotNull(it.get(spot.category)),
                    copyright = requireNotNull(it.get(spot.copyright)),
                    longitude = requireNotNull(it.get(spot.mapX)),
                    latitude = requireNotNull(it.get(spot.mapY)),
                )
            }

    /**
     * 좋아요 목록 한 페이지. `spot`을 조인해 카드에 쓸 값과 언어·카테고리를 함께 판정한다.
     * `spot_detail`은 조인하지 않는다 — 상세가 부재한 spot도 목록에는 정상적으로 나타나야 한다.
     *
     * keyset 페이징: (updated_at, spot_id) 내림차순이 정렬 키이자 커서다. `first + 1`을 읽어
     * hasNextPage를 판정한다.
     */
    /**
     * 여러 spot을 한 번에. 호출자가 목록을 들고 있을 때 행마다 묻지 않게 한다 —
     * 피드 한 페이지에 20번, 지역 피드에서는 수천 번이 될 수 있다.
     */
    @Transactional(readOnly = true)
    override fun findSpotBasicsIn(spotIds: List<Long>): Map<Long, SpotBasics> {
        if (spotIds.isEmpty()) return emptyMap()

        return queryFactory
            .select(
                spot.id, spot.title, spot.address, spot.thumbnailImageUrl,
                spot.category, spot.copyright, spot.mapX, spot.mapY,
            )
            .from(spot)
            .where(spot.id.`in`(spotIds))
            .fetch()
            .associate { row ->
                requireNotNull(row.get(spot.id)) to SpotBasics(
                    title = requireNotNull(row.get(spot.title)),
                    address = row.get(spot.address),
                    thumbnailImageUrl = requireNotNull(row.get(spot.thumbnailImageUrl)),
                    category = requireNotNull(row.get(spot.category)),
                    copyright = requireNotNull(row.get(spot.copyright)),
                    longitude = requireNotNull(row.get(spot.mapX)),
                    latitude = requireNotNull(row.get(spot.mapY)),
                )
            }
    }

    override fun findLikedSpots(query: LikedSpotsSearch): CursorPage<LikedSpotView> {
        val cursor = query.after?.let { decodeCursor(it) }

        val rows = queryFactory
            .select(
                spot.id, spot.title, spot.thumbnailImageUrl, spot.address,
                spot.category, spot.copyright, reaction.updatedAt,
            )
            .from(reaction)
            .join(spot).on(spot.id.eq(reaction.spotId))
            .where(
                reaction.memberId.eq(query.memberId),
                reaction.reaction.eq(ReactionType.LIKE),
                spot.language.eq(query.language),
                query.category?.let { spot.category.eq(it) },
                // 정렬이 (updated_at DESC, spot_id DESC)이므로 다음 페이지는 그 튜플보다 "작은" 행이다.
                // 총계에는 붙이지 않는다 — 커서는 페이지 경계일 뿐 집합의 조건이 아니다.
                cursor?.let {
                    reaction.updatedAt.lt(it.updatedAt)
                        .or(reaction.updatedAt.eq(it.updatedAt).and(reaction.spotId.lt(it.spotId)))
                },
            )
            .orderBy(reaction.updatedAt.desc(), reaction.spotId.desc())
            .limit((query.first + 1).toLong())
            .fetch()
            .map {
                val copyright = requireNotNull(it.get(spot.copyright))
                LikedSpotView(
                    id = requireNotNull(it.get(spot.id)),
                    title = requireNotNull(it.get(spot.title)),
                    thumbnailImageUrl = requireNotNull(it.get(spot.thumbnailImageUrl)),
                    address = it.get(spot.address),
                    category = requireNotNull(it.get(spot.category)),
                    copyright = copyright,
                    // copyright에서 도출되는 값이라 적재하지 않는다(둘이 어긋날 여지를 없앤다).
                    attributionText = copyright.attributionText,
                    likedAt = requireNotNull(it.get(reaction.updatedAt)),
                )
            }

        val hasNextPage = rows.size > query.first
        val pageRows = if (hasNextPage) rows.take(query.first) else rows
        val edges = pageRows.map { CursorEdge(cursor = encodeCursor(it.likedAt, it.id), node = it) }

        return CursorPage(
            edges = edges,
            pageInfo = CursorPageInfo(
                hasNextPage = hasNextPage,
                hasPreviousPage = cursor != null,
                startCursor = edges.firstOrNull()?.cursor,
                endCursor = edges.lastOrNull()?.cursor,
            ),
            // 좋아요 목록은 총 개수를 노출하지 않는다 — 소진 판정은 hasNextPage로 충분하다.
            totalCount = null,
        )
    }
}
