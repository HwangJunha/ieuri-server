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
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.ReactionType
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotReactionEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.sql.Timestamp
import java.time.Instant
import java.util.Base64

/**
 * 관광지 읽기 포트 구현(CQRS 읽기). 애그리거트를 우회해 필요한 값만 프로젝션한다.
 * 읽기 소스는 두 테이블이다: spot(반경 조회) + spot_reaction(반응 필터·좋아요 집계).
 *
 * **반경 조회만 네이티브 SQL을 쓴다.** PostGIS 함수(ST_DWithin·ST_Distance)는 QueryDSL이 모르고,
 * 억지로 표현하면 오히려 읽기 어려워진다. 나머지는 기존대로 QueryDSL 프로젝션이다.
 */
@Component
class SpotQueryAdapter(
    private val queryFactory: JPAQueryFactory,
    private val jdbcTemplate: JdbcTemplate,
) : SpotQueryPort {

    private val reaction = QSpotReactionEntity.spotReactionEntity

    override fun searchNearby(query: NearbySpotSearch): List<SpotSearchResult> {
        val (where, params) = whereClause(query)
        // 중심 좌표를 CTE로 **한 번만** 바인딩한다. SELECT·WHERE·ORDER BY 세 곳에서 쓰이는데
        // 그때마다 ST_MakePoint(?, ?)를 반복하면 위치 파라미터가 6개로 늘어 순서를 맞추기 어렵고,
        // 한 곳만 바뀌어도 조용히 어긋난다. CTE는 그 결합을 끊는다.
        val sql = """
            WITH c AS (SELECT $CENTER AS center)
            SELECT id, title, address, thumbnail_image_url, map_x, map_y,
                   category, copyright,
                   ST_Distance(location, c.center) AS distance_meters
            FROM spot, c
            WHERE $where
            ORDER BY ST_Distance(location, c.center), id
            LIMIT ? OFFSET ?
        """.trimIndent()

        // 정렬용으로 계산한 거리를 SELECT에서 재사용한다(중복 계산 없음).
        // ORDER BY의 ", id"를 빠뜨리면 등거리 두 행의 순서가 요청마다 달라진다 —
        // 오프셋 페이지네이션에서는 항목 중복·누락으로 이어지는 회귀다(런타임 계약 위험 9번, POL-019).
        return jdbcTemplate.query(
            sql,
            { rs, _ ->
                SpotSearchResult(
                    spotId = rs.getLong("id"),
                    title = rs.getString("title"),
                    address = rs.getString("address"),
                    thumbnailImageUrl = rs.getString("thumbnail_image_url"),
                    mapX = rs.getDouble("map_x"),
                    mapY = rs.getDouble("map_y"),
                    category = SpotCategory.valueOf(rs.getString("category")),
                    copyright = CopyrightType.valueOf(rs.getString("copyright")),
                    distanceMeters = rs.getDouble("distance_meters"),
                )
            },
            *(params + query.limit + query.offset).toTypedArray(),
        )
    }

    override fun countNearby(query: NearbySpotSearch): Int {
        val (where, params) = whereClause(query)
        return jdbcTemplate.queryForObject(
            "WITH c AS (SELECT $CENTER AS center) SELECT COUNT(*) FROM spot, c WHERE $where",
            Int::class.java,
            *params.toTypedArray(),
        ) ?: 0
    }

    /**
     * 조회와 카운트가 **같은 조건**을 쓰도록 한 곳에서 만든다. 둘이 갈리면 hasNext와 totalCount가
     * 서로 어긋나는 상태가 생긴다(런타임 계약 위험 3번 — 기존에는 둘 다 같은 upstream 응답에서
     * 나와 자연히 일관됐다).
     *
     * 반경 필터는 반드시 ST_DWithin이다. ST_Distance(...) <= radius로 쓰면 전 행을 계산해
     * GiST 인덱스를 못 탄다(AC-24 위반).
     */
    private fun whereClause(query: NearbySpotSearch): Pair<String, List<Any>> {
        // 파라미터 순서: CTE의 center(경도, 위도)가 먼저 오고, 그다음 WHERE 조건 순서다.
        val conditions = mutableListOf("language = ?", "ST_DWithin(location, c.center, ?)")
        val params = mutableListOf<Any>(
            query.center.longitude, query.center.latitude, // CTE의 ST_MakePoint(경도, 위도)
            query.language.name,
            query.radiusMeters.toDouble(),
        )

        query.category?.let {
            conditions += "category = ?"
            params += it.name
        }
        if (query.excludedSpotIds.isNotEmpty()) {
            // 이미 반응한 항목은 DB 단계에서 제외한다 — 조회 후 걸러내면 페이지가 size보다 작아진다.
            conditions += "id NOT IN (${query.excludedSpotIds.joinToString(",") { "?" }})"
            params.addAll(query.excludedSpotIds)
        }
        return conditions.joinToString(" AND ") to params
    }

    private companion object {
        /** 요청 좌표를 geography 점으로. ST_MakePoint는 (경도, 위도) 순서다 — 뒤집으면 조용히 틀린다. */
        const val CENTER = "ST_MakePoint(?, ?)::geography"
    }

    /**
     * **`spot`을 조인해 언어를 판별한다**(V20). `spot_reaction`에서 `language` 컬럼이 사라졌기
     * 때문이다 — 언어는 이제 `spot` 행이 소유하며, 반응은 `spot_id`로만 그것을 가리킨다.
     *
     * QueryDSL이 아니라 JDBC를 쓰는 이유는 `spot`에 Q-클래스 조인 정의가 없어서다. 조인 하나를
     * 위해 엔티티 연관을 새로 얹는 것보다 이 편이 가볍다.
     */
    override fun findReactedSpotIds(memberId: Long, language: DomainLocale): Set<Long> =
        jdbcTemplate.queryForList(
            """
            SELECT r.spot_id FROM spot_reaction r
            JOIN spot s ON s.id = r.spot_id
            WHERE r.member_id = ? AND s.language = ?
            """.trimIndent(),
            Long::class.java,
            memberId, language.name,
        ).filterNotNull().toSet()

    /**
     * LIKE 반응 수(SPEC-0003 §5). **언어 조건이 사라졌다** — `spotId`가 이미 언어별이므로
     * 언어를 다시 거르면 중복이다(V20).
     */
    override fun countLikes(spotId: Long): Long =
        queryFactory
            .select(reaction.count())
            .from(reaction)
            .where(
                reaction.spotId.eq(spotId),
                reaction.reaction.eq(ReactionType.LIKE),
            )
            .fetchOne() ?: 0L

    /**
     * `spot.id` → upstream 식별 정보(V20). 상세 조회가 캐시·DB를 모두 놓쳤을 때만 호출된다 —
     * TourAPI는 우리 PK를 모르므로 `contentId`로 물어야 한다.
     */
    override fun findIdentity(spotId: Long): SpotIdentity? =
        jdbcTemplate.query(
            "SELECT content_id, language FROM spot WHERE id = ?",
            { rs, _ ->
                SpotIdentity(
                    contentId = rs.getString("content_id"),
                    language = DomainLocale.valueOf(rs.getString("language")),
                )
            },
            spotId,
        ).firstOrNull()

    /**
     * 상세 본문이 부재할 때 응답을 구성할 `spot` 행의 값(SPEC-0008 R-2). title·thumbnail_image_url·
     * category·copyright가 DB에서 NOT NULL이라 본문이 없어도 non-null 계약을 지킬 수 있다(AC-5·AC-6).
     */
    override fun findSpotBasics(spotId: Long): SpotBasics? =
        jdbcTemplate.query(
            "SELECT title, address, thumbnail_image_url, category, copyright FROM spot WHERE id = ?",
            { rs, _ ->
                SpotBasics(
                    title = rs.getString("title"),
                    address = rs.getString("address"),
                    thumbnailImageUrl = rs.getString("thumbnail_image_url"),
                    category = SpotCategory.valueOf(rs.getString("category")),
                    copyright = CopyrightType.valueOf(rs.getString("copyright")),
                )
            },
            spotId,
        ).firstOrNull()

    /**
     * 좋아요 목록 한 페이지(SPEC-0008 R-15·R-16).
     *
     * **`spot`을 조인하는 이유가 둘이다.** (1) 목록 카드에 쓸 값이 전부 `spot` 행에 있고,
     * (2) V20 이후 `spot_reaction`에 language가 없어 언어 한정을 이 조인으로 판정한다(AC-49) —
     * 종료 축제 조회가 같은 이유로 `spot`을 조인하는 것과 같은 패턴이다.
     *
     * `spot_detail`은 **조인하지 않는다** — 좋아요 목록은 상세와 무관하므로 상세가 부재한 spot도
     * 정상적으로 나타나야 한다.
     *
     * keyset 페이징: (updated_at, spot_id) 내림차순이 정렬 키이자 커서다. `first + 1`을 읽어
     * hasNextPage를 판정하는 것은 `members`의 방식과 같다. QueryDSL이 아니라 JDBC인 것도 이 어댑터의
     * 기존 선택과 같다 — `spot`에 Q-클래스가 없다.
     */
    override fun findLikedSpots(query: LikedSpotsSearch): CursorPage<LikedSpotView> {
        val cursor = query.after?.let { decodeCursor(it) }
        val (baseWhere, baseParams) = likedWhereClause(query)

        // keyset 조건. 정렬이 (updated_at DESC, spot_id DESC)이므로 다음 페이지는 그 튜플보다 "작은" 행이다.
        // 튜플 비교로 쓰면 복합 인덱스를 그대로 탄다 — OR로 풀어쓰면 옵티마이저가 놓칠 수 있다.
        // **총계에는 붙이지 않는다** — 커서는 페이지 경계일 뿐 집합의 조건이 아니다.
        val keysetCondition = cursor?.let { " AND (r.updated_at, r.spot_id) < (?, ?)" }.orEmpty()
        val keysetParams = cursor?.let { listOf(Timestamp.from(it.updatedAt), it.spotId) }.orEmpty()

        val rows = jdbcTemplate.query(
            """
            SELECT s.id, s.title, s.thumbnail_image_url, s.address, s.category, s.copyright,
                   r.updated_at AS liked_at
            FROM spot_reaction r
            JOIN spot s ON s.id = r.spot_id
            WHERE $baseWhere$keysetCondition
            ORDER BY r.updated_at DESC, r.spot_id DESC
            LIMIT ?
            """.trimIndent(),
            { rs, _ ->
                val copyright = CopyrightType.valueOf(rs.getString("copyright"))
                LikedSpotView(
                    id = rs.getLong("id"),
                    title = rs.getString("title"),
                    thumbnailImageUrl = rs.getString("thumbnail_image_url"),
                    address = rs.getString("address"),
                    category = SpotCategory.valueOf(rs.getString("category")),
                    copyright = copyright,
                    // copyright에서 도출되는 값이라 적재하지 않는다(둘이 어긋날 여지를 없앤다).
                    attributionText = copyright.attributionText,
                    likedAt = rs.getTimestamp("liked_at").toInstant(),
                )
            },
            *(baseParams + keysetParams + (query.first + 1)).toTypedArray(),
        )

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
            // 좋아요 목록은 총 개수를 노출하지 않는다(LikedSpotConnection에 필드가 없다) —
            // 소진 판정은 hasNextPage로 충분하고, COUNT(*)는 페이지마다 스캔을 하나 더 쓴다.
            totalCount = null,
        )
    }

    /**
     * 언어 판정은 `spot` 조인으로 한다 — V20 이후 `spot_reaction`에 language가 없다.
     * 카테고리도 `spot`이 소유하므로 같은 조인을 그대로 쓴다.
     */
    private fun likedWhereClause(query: LikedSpotsSearch): Pair<String, List<Any>> {
        val conditions = mutableListOf("r.member_id = ?", "r.reaction = ?", "s.language = ?")
        val params = mutableListOf<Any>(query.memberId, ReactionType.LIKE.name, query.language.name)
        query.category?.let {
            conditions += "s.category = ?"
            params += it.name
        }
        return conditions.joinToString(" AND ") to params
    }

    /**
     * 커서 = 정렬 키(updated_at, spot_id)를 Base64로 감싼 값.
     *
     * **불투명성은 값을 감추려는 것이 아니다**(POL-021) — 두 구성 요소 모두 응답에 이미 있다
     * (`LikedSpotEdge.likedAt`, `LikedSpot.id`). 목적은 클라이언트가 커서를 **파싱·조립하지 못하게**
     * 막는 것이다. 형식이 드러나면 클라이언트가 직접 커서를 만들기 시작하고, 그 순간 정렬 키를
     * 바꾸는 것이 파괴적 변경이 된다. `members`가 회원 id를 그대로 커서로 쓴 것은 그것이 단일
     * 공개 식별자였기 때문이고, 복합 키인 여기서는 같은 선택을 할 수 없다.
     *
     * 시각은 `Instant.toString()`(ISO-8601)으로 담아 나노초까지 보존한다 — TIMESTAMPTZ가
     * 마이크로초 정밀도라 잘라내면 같은 마이크로초의 두 행에서 커서가 어긋나 항목을 건너뛴다.
     */
    private fun encodeCursor(likedAt: Instant, spotId: Long): String =
        Base64.getUrlEncoder().withoutPadding()
            .encodeToString("$likedAt|$spotId".toByteArray(StandardCharsets.UTF_8))

    private fun decodeCursor(cursor: String): LikedCursor =
        try {
            val decoded = String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
            val (instant, spotId) = decoded.split("|", limit = 2)
            LikedCursor(updatedAt = Instant.parse(instant), spotId = spotId.toLong())
        } catch (e: RuntimeException) {
            // 조작·구버전 커서. 조용히 첫 페이지로 되돌리면 클라이언트가 무한 루프에 빠질 수 있어
            // 명시적으로 거부한다(members의 커서 처리와 같은 태도).
            throw IllegalArgumentException("유효하지 않은 커서입니다.", e)
        }

    /** 디코딩된 keyset 커서. */
    private data class LikedCursor(val updatedAt: Instant, val spotId: Long)
}
