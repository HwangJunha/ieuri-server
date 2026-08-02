package org.around.ieuri.tourism.infrastructure.adapter.out.query

import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.tourism.application.port.out.query.NearbySpotSearch
import org.around.ieuri.tourism.application.port.out.query.SpotQueryPort
import org.around.ieuri.tourism.application.port.out.query.SpotSearchResult
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.ReactionType
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotReactionEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

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
            SELECT content_id, title, address, thumbnail_image_url, map_x, map_y,
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
                    contentId = rs.getString("content_id"),
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
        if (query.excludedContentIds.isNotEmpty()) {
            // 이미 반응한 항목은 DB 단계에서 제외한다 — 조회 후 걸러내면 페이지가 size보다 작아진다.
            conditions += "content_id NOT IN (${query.excludedContentIds.joinToString(",") { "?" }})"
            params.addAll(query.excludedContentIds)
        }
        return conditions.joinToString(" AND ") to params
    }

    private companion object {
        /** 요청 좌표를 geography 점으로. ST_MakePoint는 (경도, 위도) 순서다 — 뒤집으면 조용히 틀린다. */
        const val CENTER = "ST_MakePoint(?, ?)::geography"
    }

    override fun findReactedContentIds(memberId: Long, language: DomainLocale): Set<String> =
        queryFactory
            .select(reaction.contentId)
            .from(reaction)
            .where(
                reaction.memberId.eq(memberId),
                reaction.language.eq(language),
            )
            .fetch()
            .toSet()

    override fun countLikes(contentId: String, language: DomainLocale): Long =
        queryFactory
            .select(reaction.count())
            .from(reaction)
            .where(
                reaction.contentId.eq(contentId),
                reaction.language.eq(language),
                reaction.reaction.eq(ReactionType.LIKE),
            )
            .fetchOne() ?: 0L
}
