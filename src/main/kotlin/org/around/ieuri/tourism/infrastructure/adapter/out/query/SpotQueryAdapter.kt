package org.around.ieuri.tourism.infrastructure.adapter.out.query

import com.querydsl.core.types.dsl.Expressions
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
import org.around.ieuri.tourism.domain.vo.CopyrightType
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
     * 그 회원이 이미 반응한 **카탈로그 id**. 반경 조회가 이 집합을 제외 조건으로 쓴다.
     *
     * **`spot`을 경유하지 않는다**(SPEC-0019). 반응이 이미 카탈로그 단위로 적재되고, 목록이
     * 내보내는 id도 카탈로그 id라 두 축이 그대로 맞는다 — 예전에는 언어별 `spot.id`로 되돌리기
     * 위해 `spot`을 조인했으나, 그 조인은 그 언어의 spot 행이 없는 카탈로그를 제외 집합에서
     * 빠뜨려 이미 반응한 항목이 다시 나오게 만든다.
     *
     * **언어를 받지 않는다** — 좋아요는 언어와 무관한 장소 단위 행위다.
     */
    override fun findReactedSpotIds(memberId: Long): Set<Long> =
        queryFactory
            .select(reaction.catalogId)
            .from(reaction)
            .where(reaction.memberId.eq(memberId))
            .fetch()
            .toSet()

    /**
     * LIKE 반응 수. **카탈로그 단위 집계다**(SPEC-0019 R-10) — 같은 장소의 언어별 좋아요가
     * 합산되어 **응답값이 커진다.** 의도된 교정이며, 지금까지 장소당 좋아요 수를 실제보다
     * 작게 보고하고 있었던 것이다.
     *
     * 인자는 카탈로그 id다 — 반응 테이블이 이미 그 축이라 `spot`을 경유할 이유가 없다.
     */
    override fun countLikes(spotId: Long): Long =
        queryFactory
            .select(reaction.count())
            .from(reaction)
            .where(
                reaction.catalogId.eq(spotId),
                reaction.reaction.eq(ReactionType.LIKE),
            )
            .fetchOne() ?: 0L

    /**
     * (카탈로그 id, 언어) → upstream 식별 정보. 상세 조회가 캐시·DB를 모두 놓쳤을 때만 쓴다.
     *
     * **`spot`을 읽는 유일한 조회 경로다**(SPEC-0019). TourAPI의 contentId가 언어별이라
     * 그 언어의 원본 행을 거쳐야만 상세를 물을 수 있다 — `idx_spot_catalog_language`
     * (spot 테이블의 `(catalog_id, language)` 복합 인덱스)가 이 조회를 받친다.
     *
     * 번역만 있고 그 언어의 원본이 없는 카탈로그는 null이며, 호출자가 "상세 없음"으로 해석한다.
     */
    override fun findIdentity(catalogId: Long, language: DomainLocale): SpotIdentity? =
        queryFactory
            .select(spot.id, spot.contentId, spot.language)
            .from(spot)
            .where(spot.catalogId.eq(catalogId), spot.language.eq(language))
            .fetchFirst()
            ?.let {
                SpotIdentity(
                    spotId = requireNotNull(it.get(spot.id)),
                    contentId = requireNotNull(it.get(spot.contentId)),
                    language = requireNotNull(it.get(spot.language)),
                )
            }

    /**
     * 상세 본문이 부재할 때 응답을 구성할 카탈로그의 값. 인자는 **카탈로그 id**다(SPEC-0019).
     *
     * 제목·주소는 [language] 번역을 우선하되, **없으면 다른 언어로 떨어진다.** 외국어에만
     * 있는 장소는 KO 행이 없어(실측 4,196건) null을 돌려주면 그 장소에 글을 못 쓰고 이미
     * 쓴 글의 칩도 사라진다 — 이름이 다른 언어인 편이 없는 것보다 낫다.
     *
     * 카탈로그가 없으면 null이다. 번역이 하나도 없는 카탈로그는 원본이 사라진 것이며
     * (배정 배치가 번역을 지운다) 그 역시 null이 되어 조회에서 빠진다.
     */
    override fun findSpotBasics(catalogId: Long, language: DomainLocale): SpotBasics? =
        queryFactory
            .select(
                translation.title, translation.address, catalog.thumbnailImageUrl,
                catalog.category, catalog.copyright, catalog.mapX, catalog.mapY,
            )
            .from(catalog)
            .join(translation).on(translation.catalogId.eq(catalog.id))
            .where(catalog.id.eq(catalogId))
            // **요청 언어를 우선하되 없으면 다른 언어로 떨어진다.** 외국어에만 있는 장소를
            // 한국어로 물으면 KO 행이 없는데(실측 4,196건), 그때 null을 돌려주면 그 장소에
            // 글을 못 쓰고 이미 쓴 글의 칩도 사라진다 — 이름은 다른 언어라도 있는 편이 낫다.
            .orderBy(preferredLanguageOrder(language).asc(), translation.language.asc())
            .fetchFirst()
            ?.let {
                SpotBasics(
                    title = requireNotNull(it.get(translation.title)),
                    address = it.get(translation.address),
                    thumbnailImageUrl = requireNotNull(it.get(catalog.thumbnailImageUrl)),
                    category = requireNotNull(it.get(catalog.category)),
                    copyright = requireNotNull(it.get(catalog.copyright)),
                    longitude = requireNotNull(it.get(catalog.mapX)),
                    latitude = requireNotNull(it.get(catalog.mapY)),
                )
            }

    /**
     * 요청 언어를 0, 나머지를 1로 매기는 정렬 식. 요청 언어 행이 있으면 그것이 먼저 온다.
     *
     * CASE 식이라 인덱스를 타지 않지만, 대상이 카탈로그 하나(또는 IN 목록)로 이미 좁혀진
     * 뒤라 비용이 없다.
     */
    private fun preferredLanguageOrder(language: DomainLocale) =
        Expressions.cases()
            .`when`(translation.language.eq(language)).then(0)
            .otherwise(1)

    /**
     * 좋아요 목록 한 페이지. `spot`을 조인해 카드에 쓸 값과 언어·카테고리를 함께 판정한다.
     * `spot_detail`은 조인하지 않는다 — 상세가 부재한 spot도 목록에는 정상적으로 나타나야 한다.
     *
     * keyset 페이징: (updated_at, spot_id) 내림차순이 정렬 키이자 커서다. `first + 1`을 읽어
     * hasNextPage를 판정한다.
     */
    /**
     * 여러 카탈로그를 한 번에. 호출자가 목록을 들고 있을 때 행마다 묻지 않게 한다 —
     * 피드 한 페이지에 20번, 지역 피드에서는 수천 번이 될 수 있다.
     *
     * [language] 번역을 우선하되 없으면 다른 언어로 떨어진다([findSpotBasics]와 같은 판단).
     * 번역이 하나도 없는 카탈로그만 결과에서 빠지며, 호출자는 그것을 "유효하지 않은 id"와
     * 같게 다룬다.
     */
    @Transactional(readOnly = true)
    override fun findSpotBasicsIn(catalogIds: List<Long>, language: DomainLocale): Map<Long, SpotBasics> {
        if (catalogIds.isEmpty()) return emptyMap()

        return queryFactory
            .select(
                catalog.id, translation.title, translation.address, catalog.thumbnailImageUrl,
                catalog.category, catalog.copyright, catalog.mapX, catalog.mapY,
            )
            .from(catalog)
            .join(translation).on(translation.catalogId.eq(catalog.id))
            .where(catalog.id.`in`(catalogIds))
            // **요청 언어 우선, 없으면 다른 언어.** [findSpotBasics]와 같은 판단이다.
            // associate가 뒤 값으로 덮으므로 우선순위가 낮은 것부터 오게 정렬한다.
            .orderBy(preferredLanguageOrder(language).desc(), translation.language.desc())
            .fetch()
            .associate { row ->
                requireNotNull(row.get(catalog.id)) to SpotBasics(
                    title = requireNotNull(row.get(translation.title)),
                    address = row.get(translation.address),
                    thumbnailImageUrl = requireNotNull(row.get(catalog.thumbnailImageUrl)),
                    category = requireNotNull(row.get(catalog.category)),
                    copyright = requireNotNull(row.get(catalog.copyright)),
                    longitude = requireNotNull(row.get(catalog.mapX)),
                    latitude = requireNotNull(row.get(catalog.mapY)),
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
                catalog.id, translation.title, translation.address,
                catalog.thumbnailImageUrl, catalog.category, catalog.copyright,
                reaction.updatedAt,
            )
            .from(reaction)
            // **제목·주소는 번역에서 온다**(R-4·R-9). 이 조인이 카탈로그당 정확히 1행을
            // 보장하며(UNIQUE(catalog_id, language)), 번역이 없는 언어는 결과에서 빠진다 —
            // 사용자가 읽을 수 없는 항목을 목록에 넣지 않는다는 규칙 그대로다.
            //
            // **`spot`을 조인하지 않는다.** 예전에는 `spot.language`로 걸렀는데, 그러면 번역이
            // 채워져도 그 언어의 spot 행이 없으면 목록에 나오지 않는다 — 이 스펙 목적의
            // 절반이 무효가 된다.
            .join(translation).on(
                translation.catalogId.eq(reaction.catalogId),
                translation.language.eq(query.language),
            )
            // 좌표·썸네일·분류는 카탈로그가 확정해 둔 표준값이다.
            //
            // **원본이 사라진 장소도 여기서는 보인다.** 번역 행이 지워져도 좋아요는 남고,
            // 이 조회는 번역을 INNER JOIN하므로 그때는 자연히 빠진다 — 다만 카탈로그와
            // 좋아요 자체는 보존되어 상류가 그 장소를 다시 주면 되살아난다.
            .join(catalog).on(catalog.id.eq(reaction.catalogId))
            .where(
                reaction.memberId.eq(query.memberId),
                reaction.reaction.eq(ReactionType.LIKE),
                query.category?.let { catalog.category.eq(it) },
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
                val copyright = requireNotNull(it.get(catalog.copyright))
                val catalogId = requireNotNull(it.get(catalog.id))
                LikedSpotView(
                    // **클라이언트에 나가는 것은 카탈로그 id다.** 좋아요가 장소 단위이므로
                    // 언어별 spot.id를 돌려줄 이유가 없다.
                    id = catalogId,
                    title = requireNotNull(it.get(translation.title)),
                    thumbnailImageUrl = requireNotNull(it.get(catalog.thumbnailImageUrl)),
                    address = it.get(translation.address),
                    category = requireNotNull(it.get(catalog.category)),
                    copyright = copyright,
                    // copyright에서 도출되는 값이라 적재하지 않는다(둘이 어긋날 여지를 없앤다).
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
            // 좋아요 목록은 총 개수를 노출하지 않는다 — 소진 판정은 hasNextPage로 충분하다.
            totalCount = null,
        )
    }
}
