package org.around.ieuri.tourism.infrastructure.adapter.out.query

import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.common.pagination.CursorEdge
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.CursorPageInfo
import org.around.ieuri.tourism.application.port.out.query.LikedSpotsSearch
import org.around.ieuri.tourism.application.port.out.query.ReactionCounts
import org.around.ieuri.tourism.application.port.out.query.SpotBasics
import org.around.ieuri.tourism.application.port.out.query.SpotIdentity
import org.around.ieuri.tourism.application.port.out.query.SpotQueryPort
import org.around.ieuri.tourism.application.exception.InvalidCursorException
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
 * 읽기 소스는 두 테이블이다: spot(관광지 본체) + spot_reaction(반응 필터·좋아요 집계).
 *
 * 반경 조회는 PostGIS가 필요해 생 SQL이며, `SpotSpatialQueryPort`(→ [SpotSpatialQueryAdapter])로
 * 갈라져 있다 — 호출자가 두 포트를 함께 주입받는다.
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
     * 그 회원이 이미 반응한 **카탈로그 id**. 반경 조회가 이 집합을 제외 조건으로 쓴다.
     *
     * **`spot`을 경유하지 않는다**(SPEC-0019). 반응이 이미 카탈로그 단위로 적재되고, 목록이
     * 내보내는 id도 카탈로그 id라 두 축이 그대로 맞는다 — 예전에는 언어별 `spot.id`로 되돌리기
     * 위해 `spot`을 조인했으나, 그 조인은 그 언어의 spot 행이 없는 카탈로그를 제외 집합에서
     * 빠뜨려 이미 반응한 항목이 다시 나오게 만든다.
     *
     * **[language]로 한정한다**(SPEC-0029 R-13). 반응이 언어별로 갈리므로 제외 집합도 같은
     * 축이어야 한다 — 언어를 빼면 영어로 반응한 장소가 일본어 발견 큐에서도 사라진다.
     */
    override fun findReactedSpotIds(memberId: Long, language: DomainLocale): Set<Long> =
        queryFactory
            .select(reaction.catalogId)
            .from(reaction)
            .where(reaction.memberId.eq(memberId), reaction.language.eq(language))
            .fetch()
            .toSet()

    /**
     * LIKE 반응 수. **활성 언어 안에서만 센다**(SPEC-0029 AC-13.5) — 다른 언어의 좋아요는
     * 합산되지 않는다. SPEC-0019가 언어를 합쳤던 것을 이 스펙이 되돌린다.
     *
     * 인자는 카탈로그 id다 — 반응 테이블이 이미 그 축이라 `spot`을 경유할 이유가 없다.
     */
    override fun countLikes(catalogId: Long, language: DomainLocale): Long =
        queryFactory
            .select(reaction.count())
            .from(reaction)
            .where(
                reaction.catalogId.eq(catalogId),
                reaction.language.eq(language),
                reaction.reaction.eq(ReactionType.LIKE),
            )
            .fetchOne() ?: 0L

    /**
     * 여러 카탈로그의 LIKE·DISLIKE 수를 한 번에. **활성 언어 안에서만 센다**([countLikes]와 같다).
     * GROUP BY에 없는 id는 0으로 채운다 — 반응이 없는 것이지 모르는 장소가 아니다.
     */
    override fun countReactionsIn(catalogIds: Collection<Long>, language: DomainLocale): Map<Long, ReactionCounts> {
        if (catalogIds.isEmpty()) return emptyMap()

        val counted: Map<Pair<Long, ReactionType?>, Long> = queryFactory
            .select(reaction.catalogId, reaction.reaction, reaction.count())
            .from(reaction)
            .where(reaction.catalogId.`in`(catalogIds), reaction.language.eq(language))
            .groupBy(reaction.catalogId, reaction.reaction)
            .fetch()
            .associate { row ->
                (requireNotNull(row.get(reaction.catalogId)) to row.get(reaction.reaction)) to (row.get(reaction.count()) ?: 0L)
            }
        return catalogIds.associateWith { id ->
            ReactionCounts(
                likes = counted[id to ReactionType.LIKE] ?: 0L,
                dislikes = counted[id to ReactionType.DISLIKE] ?: 0L,
            )
        }
    }

    /**
     * 이 회원이 이 언어에서 남긴 최신 반응. 반응한 적 없으면 null이며 그것이 정상적인 부재다.
     *
     * `(member_id, catalog_id, language)`에 행이 하나뿐이라(덮어쓰기만 가능) 정렬 없이 단건으로
     * 읽는다. 영어로 누른 좋아요를 일본어로 물으면 null이다(AC-13.2).
     */
    @Transactional(readOnly = true)
    override fun findReaction(catalogId: Long, memberId: Long, language: DomainLocale): ReactionType? =
        queryFactory
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
     *
     * **`spot`을 읽는 유일한 조회 경로다**(SPEC-0019). TourAPI의 contentId가 언어별이라
     * 원본 행을 거쳐야만 상세를 물을 수 있다 — `idx_spot_catalog_language`
     * (spot 테이블의 `(catalog_id, language)` 복합 인덱스)가 이 조회를 받친다.
     *
     * **[language] 원본만 본다 — 폴백하지 않는다**(SPEC-0029 R-10). 그 언어에 원본이 없으면
     * null이며, 호출자가 그것을 `SPOT_NOT_FOUND_IN_LOCALE`로 옮긴다. 없는 것은 없는 것이지
     * 다른 언어 값으로 대신할 것이 아니다(POL-043).
     *
     * **좌표는 원본 `spot`이 아니라 `spot_catalog`에서 읽는다**(SPEC-0027). 카탈로그 PK 조인이라
     * 조회가 늘지 않는다.
     *
     * 이 조인은 **nullable 컬럼(`spot.catalog_id`, ON DELETE SET NULL)에 건 INNER 조인이다.**
     * where가 `catalogId`(non-null)로 이미 좁혀 두어 안전하지만, 그 조건을 완화하면 조인이
     * 필터로 바뀌어 배정되지 않은 spot이 조용히 null이 된다.
     */
    override fun findIdentity(catalogId: Long, language: DomainLocale): SpotIdentity? =
        queryFactory
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

    /**
     * 카탈로그 존재 여부 — 언어와 무관하다. 두 오류 코드를 가르기 위해서만 쓴다(AC-12.3).
     */
    override fun existsCatalog(catalogId: Long): Boolean =
        queryFactory
            .selectOne()
            .from(catalog)
            .where(catalog.id.eq(catalogId))
            .fetchFirst() != null

    /**
     * 상세 본문이 부재할 때 응답을 구성할 카탈로그의 값. 인자는 **카탈로그 id**다(SPEC-0019).
     *
     * 제목·주소는 **[language] 번역만 본다 — 폴백하지 않는다**(SPEC-0029 R-10). 그 언어에
     * 원본이 없으면 null이고, 호출자가 그것을 "이 언어에 없는 장소"로 다룬다.
     *
     * 카탈로그가 없어도 null이다 — 두 경우를 구분해야 하는 호출자는 [existsCatalog]를 함께 본다.
     */
    override fun findSpotBasics(catalogId: Long, language: DomainLocale): SpotBasics? =
        queryFactory
            .select(
                translation.title, translation.address, catalog.thumbnailImageUrl,
                catalog.category, catalog.copyright, catalog.mapX, catalog.mapY,
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
     * **[language]에서 해소되지 않는 카탈로그는 결과에서 빠진다**(SPEC-0029 R-10·AC-24.3).
     * 호출자는 빠진 id를 "이 언어에 없는 장소"로 다룬다 — 키가 없는 것이 곧 그 뜻이다.
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
            // **여기만 `visible`을 걸지 않는다.** 원본이 사라진 장소라도 사용자가 누른
            // 좋아요는 보여야 한다 — 감추면 "내가 누른 것이 사라진" 것처럼 보이고,
            // 좋아요를 지키려고 카탈로그를 남긴 판단과 어긋난다(사용자 결정 2026-08-15).
            //
            // 번역 행은 감춰질 뿐 지워지지 않으므로 이 조인은 그대로 값을 찾는다.
            .join(catalog).on(catalog.id.eq(reaction.catalogId))
            .where(
                reaction.memberId.eq(query.memberId),
                reaction.reaction.eq(ReactionType.LIKE),
                // **반응 자체가 언어를 품는다**(SPEC-0029 AC-14.1/14.2). 번역 조인의 언어와
                // 같은 값이지만 역할이 다르다 — 저쪽은 "무슨 제목으로 보일지", 이쪽은
                // "어느 언어에서 누른 것인지"다. 이 조건이 빠지면 다른 언어에서 누른
                // 좋아요가 이 언어의 목록에 섞인다.
                reaction.language.eq(query.language),
                query.categories.takeIf { !it.isUnfiltered }?.let { catalog.category.`in`(it.codes()) },
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
                    category = SpotCategory(requireNotNull(it.get(catalog.category))),
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
