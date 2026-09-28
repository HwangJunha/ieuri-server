package org.around.ieuri.community.infrastructure.adapter.out.query

import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.common.pagination.CursorEdge
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.cursorPageOf
import org.around.ieuri.community.application.port.out.query.PostSpatialQueryPort
import org.around.ieuri.community.application.port.out.query.PostsSearch
import org.around.ieuri.community.application.port.out.query.ViewerContext
import org.around.ieuri.community.application.query.PostView
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QPostEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp

/**
 * 거리순 피드 전용 어댑터. **이 클래스에만 생 SQL이 있다**(tourism의 `SpotSpatialQueryAdapter`와
 * 같은 관례이며, `JdbcTemplate`이 전용 어댑터 밖으로 새지 않게 한다).
 *
 * PostGIS 함수는 QueryDSL로 표현되지 않고, `location`(geography)은 대응하는 Kotlin 타입이 없어
 * 엔티티에 매핑조차 할 수 없다 — 매핑하면 `ddl-auto=validate`가 기동을 막고, 매핑하지 않으면
 * HQL이 그 컬럼을 해석하지 못한다. map_x/map_y로 매번 점을 만들어 우회하면 표현은 되지만
 * GiST 인덱스를 타지 못한다(실측: Seq Scan 389ms 대 Index Scan 0.3ms).
 *
 * **모더레이션·카테고리·태그 조건을 이 SQL이 전부 담는다.** 일부만 담고 나머지를 뒤에서 거르면
 * 요청한 개수를 채우려고 넉넉히 읽어야 하고, 그때 몇 배를 읽을지가 근거 없는 상수가 된다.
 * 한 번에 거르면 LIMIT이 곧 정확한 페이지 크기다.
 */
@Component
class PostSpatialQueryAdapter(
    private val jdbcTemplate: JdbcTemplate,
    private val queryFactory: JPAQueryFactory,
) : PostSpatialQueryPort {

    private val post = QPostEntity.postEntity

    @Transactional(readOnly = true)
    override fun findPostsByDistance(search: PostsSearch): CursorPage<PostView> {
        val area = requireNotNull(search.area) { "거리순은 좌표를 준 경우에만 온다." }
        val cursor = search.after?.let { decodeDistanceCursor(it) }
        val params = mutableListOf<Any>()
        val conditions = mutableListOf<String>()

        // 파라미터는 SQL의 ? 등장 순서대로 쌓는다. CENTER 하나가 ?를 둘(경도·위도) 쓴다.
        fun center() {
            params += area.longitude
            params += area.latitude
        }

        center() // SELECT의 거리 계산
        center() // WHERE의 ST_DWithin
        params += area.radiusMeters.toDouble()

        conditions += "p.deleted_at IS NULL"
        // 숨김 제외 — 작성자 본인에게는 남는다(최신순 피드·단건과 같은 규칙).
        conditions += "(p.hidden = FALSE OR p.author_id = ?)"
        params += search.viewer.memberId

        // 개별 숨김. hiddenByViewer면 뒤집어 숨긴 글만 남긴다 — 그러지 않으면 해제할 방법이 없다.
        val hiddenPostIds = search.viewer.hiddenPostIds
        when {
            search.hiddenByViewer && hiddenPostIds.isEmpty() -> conditions += "FALSE"

            search.hiddenByViewer -> {
                conditions += "p.id IN (${placeholders(hiddenPostIds.size)})"
                params.addAll(hiddenPostIds)
            }

            hiddenPostIds.isNotEmpty() -> {
                conditions += "p.id NOT IN (${placeholders(hiddenPostIds.size)})"
                params.addAll(hiddenPostIds)
            }
        }

        if (search.viewer.blockedMemberIds.isNotEmpty()) {
            conditions += "p.author_id NOT IN (${placeholders(search.viewer.blockedMemberIds.size)})"
            params.addAll(search.viewer.blockedMemberIds)
        }
        // 장소 필터는 카탈로그로 건다(SPEC-0019 R-12) — spot_id로 걸면 언어별로 피드가 갈린다.
        search.catalogId?.let {
            conditions += "ps.catalog_id = ?"
            params += it
        }
        // 활성 언어 필터(SPEC-0029 R-23). **최신순 피드와 달리 "장소 없는 글" 절이 없다** —
        // 아래 FROM이 community_post_spot을 INNER JOIN하므로 거리순에는 장소 없는 글이 애초에
        // 오지 않는다(AC-15.4의 예외가 도달할 수 없다). 조건은 EXISTS 하나로 끝난다.
        conditions += """
            EXISTS (SELECT 1 FROM community.community_spot_catalog_language l
                     WHERE l.catalog_id = ps.catalog_id AND l.language = ?)
        """.trimIndent()
        params += search.language.name
        // 다중 선택은 OR이며 IN으로 표현한다(SPEC-0020). 비어 있으면 조건 자체를 걸지 않는다.
        search.categories.takeIf { it.isNotEmpty() }?.let { categories ->
            val placeholders = categories.joinToString(",") { "?" }
            conditions +=
                "EXISTS (SELECT 1 FROM community_post_chip c WHERE c.post_id = p.id AND c.category IN ($placeholders))"
            params.addAll(categories.map { it.name })
        }
        search.normalizedTag?.let {
            conditions += "EXISTS (SELECT 1 FROM community_post_tag t WHERE t.post_id = p.id AND t.normalized = ?)"
            params += it
        }
        cursor?.let {
            // 정렬이 (거리 ASC, created_at DESC, id DESC)이므로 비교도 그 순서다.
            conditions += """
                (ST_Distance(ps.location, $CENTER) > ?
                 OR (ST_Distance(ps.location, $CENTER) = ?
                     AND (p.created_at < ? OR (p.created_at = ? AND p.id < ?))))
            """.trimIndent()
            center()
            params += it.distanceMeters
            center()
            params += it.distanceMeters
            val createdAt = Timestamp.from(it.createdAt)
            params += createdAt
            params += createdAt
            params += it.id
        }

        center() // ORDER BY의 거리 계산
        params += search.first + 1

        val rows = jdbcTemplate.query(
            """
            SELECT p.id AS post_id, ST_Distance(ps.location, $CENTER) AS distance_meters
            FROM community_post p
            JOIN community_post_spot ps ON ps.post_id = p.id
            WHERE ST_DWithin(ps.location, $CENTER, ?)
              AND ${conditions.joinToString(" AND ")}
            ORDER BY ST_Distance(ps.location, $CENTER), p.created_at DESC, p.id DESC
            LIMIT ?
            """.trimIndent(),
            { rs, _ -> rs.getLong("post_id") to rs.getDouble("distance_meters") },
            *params.toTypedArray(),
        )

        val hasNextPage = rows.size > search.first
        val pageRows = if (hasNextPage) rows.take(search.first) else rows
        if (pageRows.isEmpty()) {
            return cursorPageOf(emptyList(), hasNextPage = false, hasPreviousPage = cursor != null)
        }

        // 본문은 엔티티로 읽는다 — 생 SQL은 어느 글인지와 거리만 정하고 매핑은 기존 경로를 쓴다.
        val entities = queryFactory
            .selectFrom(post)
            .where(post.id.`in`(pageRows.map { it.first }))
            .fetch()
            .associateBy { requireNotNull(it.id) }

        val edges = pageRows.map { (postId, distanceMeters) ->
            val entity = requireNotNull(entities[postId]) { "방금 조회한 글($postId)이 없다." }
            CursorEdge(
                cursor = encodeDistanceCursor(
                    distanceMeters = distanceMeters,
                    createdAt = requireNotNull(entity.createdAt),
                    id = postId,
                ),
                node = entity.toPostView(search.viewer),
            )
        }
        return cursorPageOf(edges, hasNextPage = hasNextPage, hasPreviousPage = cursor != null)
    }

    private fun placeholders(count: Int): String = List(count) { "?" }.joinToString(", ")

    private companion object {
        /** 요청 좌표를 geography 점으로. ST_MakePoint는 (경도, 위도) 순서다 — 뒤집으면 조용히 틀린다. */
        const val CENTER = "ST_MakePoint(?, ?)::geography"
    }
}
