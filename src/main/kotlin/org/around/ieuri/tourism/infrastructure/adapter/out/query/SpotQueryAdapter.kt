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
import org.around.ieuri.tourism.application.exception.InvalidCursorException
import org.around.ieuri.tourism.application.query.LikedSpotView
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.LikedSpotCursor
import org.around.ieuri.tourism.domain.vo.ReactionType
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotCatalogEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotCatalogTranslationEntity
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
    private val catalog = QSpotCatalogEntity.spotCatalogEntity
    private val translation = QSpotCatalogTranslationEntity.spotCatalogTranslationEntity

    /** 요청 언어의 spot 행. 대표 행([spot])과 구분해야 해서 별칭을 따로 둔다. */
    private val languageSpot = QSpotEntity("languageSpot")

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

    /**
     * 그 회원이 해당 언어에서 이미 반응한 관광지 id.
     *
     * 반응은 카탈로그 단위지만 **호출자가 기대하는 것은 요청 언어의 `spot.id`다**(카드 필터링에
     * 쓴다). 조인에 `language`를 포함해 카탈로그당 정확히 1행으로 되돌린다.
     */
    override fun findReactedSpotIds(memberId: Long, language: DomainLocale): Set<Long> =
        queryFactory
            .select(spot.id)
            .from(reaction)
            .join(spot).on(spot.catalogId.eq(reaction.catalogId), spot.language.eq(language))
            .where(reaction.memberId.eq(memberId))
            .fetch()
            .toSet()

    /**
     * LIKE 반응 수. **카탈로그 단위 집계다**(SPEC-0019 R-10) — 같은 장소의 언어별 좋아요가
     * 합산되어 **응답값이 커진다.** 의도된 교정이며, 지금까지 장소당 좋아요 수를 실제보다
     * 작게 보고하고 있었던 것이다.
     *
     * 인자는 여전히 `spot.id`다(계약 불변) — 그 행의 카탈로그로 해석해 센다.
     */
    override fun countLikes(spotId: Long): Long =
        queryFactory
            .select(reaction.count())
            .from(reaction)
            .join(spot).on(spot.catalogId.eq(reaction.catalogId))
            .where(
                spot.id.eq(spotId),
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
                spot.category, spot.copyright, spot.mapX, spot.mapY, spot.catalogId,
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
                    catalogId = it.get(spot.catalogId),
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
                spot.category, spot.copyright, spot.mapX, spot.mapY, spot.catalogId,
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
                    catalogId = row.get(spot.catalogId),
                )
            }
    }

    override fun findLikedSpots(query: LikedSpotsSearch): CursorPage<LikedSpotView> {
        val cursor = query.after?.let {
            // 구 커서(v2 접두사 없음)는 형식 단계에서 거부된다 — 형식이 같아 그냥 두면
            // 조용히 다른 페이지를 반환한다(SPEC-0019 §4.3).
            try {
                LikedSpotCursor.decode(it)
            } catch (e: IllegalArgumentException) {
                throw InvalidCursorException(cause = e)
            }
        }

        val rows = queryFactory
            .select(
                languageSpot.id, spot.id, translation.title,
                spot.thumbnailImageUrl, spot.address,
                spot.category, spot.copyright, reaction.updatedAt, reaction.catalogId,
            )
            .from(reaction)
            // **제목은 번역에서 온다**(SPEC-0019 R-9). 이 조인이 카탈로그당 정확히 1행을
            // 보장하며(UNIQUE(catalog_id, language)), 번역이 없는 언어는 결과에서 빠진다 —
            // 사용자가 읽을 수 없는 항목을 목록에 넣지 않는다는 규칙 그대로다.
            //
            // 이전에는 `spot.language`로 걸렀는데, 그러면 **번역이 채워져도 그 언어의 spot
            // 행이 없으면 목록에 나오지 않는다** — 이 스펙 목적의 절반이 무효가 된다.
            .join(translation).on(
                translation.catalogId.eq(reaction.catalogId),
                translation.language.eq(query.language),
            )
            // 좌표·썸네일·카테고리는 대표 행에서. 언어와 무관하게 같은 값이다(§3.6 실증).
            .join(catalog).on(catalog.id.eq(reaction.catalogId))
            .join(spot).on(spot.id.eq(catalog.representativeSpotId))
            // 요청 언어의 spot 행이 있으면 그 id를 돌려준다(계약 불변). 없으면 null이고
            // 그때는 대표 행의 id로 대신한다.
            .leftJoin(languageSpot).on(
                languageSpot.catalogId.eq(reaction.catalogId),
                languageSpot.language.eq(query.language),
            )
            .where(
                reaction.memberId.eq(query.memberId),
                reaction.reaction.eq(ReactionType.LIKE),
                query.category?.let { spot.category.eq(it) },
                // 정렬이 (updated_at DESC, catalog_id DESC)이므로 다음 페이지는 그 튜플보다 "작은" 행이다.
                // 총계에는 붙이지 않는다 — 커서는 페이지 경계일 뿐 집합의 조건이 아니다.
                cursor?.let {
                    reaction.updatedAt.lt(it.updatedAt)
                        .or(reaction.updatedAt.eq(it.updatedAt).and(reaction.catalogId.lt(it.catalogId)))
                },
            )
            .orderBy(reaction.updatedAt.desc(), reaction.catalogId.desc())
            .limit((query.first + 1).toLong())
            .fetch()
            .map {
                val copyright = requireNotNull(it.get(spot.copyright))
                LikedSpotView(
                    // 클라이언트에 나가는 것은 요청 언어의 spot.id다(계약 불변).
                    // 그 언어 행이 없는 카탈로그는 대표 행의 id로 대신한다.
                    id = it.get(languageSpot.id) ?: requireNotNull(it.get(spot.id)),
                    title = requireNotNull(it.get(translation.title)),
                    thumbnailImageUrl = requireNotNull(it.get(spot.thumbnailImageUrl)),
                    address = it.get(spot.address),
                    category = requireNotNull(it.get(spot.category)),
                    copyright = copyright,
                    // copyright에서 도출되는 값이라 적재하지 않는다(둘이 어긋날 여지를 없앤다).
                    attributionText = copyright.attributionText,
                    likedAt = requireNotNull(it.get(reaction.updatedAt)),
                    // 커서에 실린다. 정렬 키가 catalog_id이므로 spot.id로는 페이지 경계를 만들 수 없다.
                    catalogId = requireNotNull(it.get(reaction.catalogId)),
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
            // 좋아요 목록은 총 개수를 노출하지 않는다 — 소진 판정은 hasNextPage로 충분하다.
            totalCount = null,
        )
    }
}
