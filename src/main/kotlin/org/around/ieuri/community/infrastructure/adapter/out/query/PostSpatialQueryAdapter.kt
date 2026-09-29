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
 * 거리순 피드 전용 raw SQL 어댑터(PostGIS). `location`(geography)은 엔티티에 매핑되지 않는다.
 * - 모더레이션·카테고리·태그·언어 조건을 이 SQL이 전부 담는다 — 뒤에서 거르면 페이지 크기가 깨진다.
 * - 장소 없는 글은 오지 않는다(community_post_spot INNER JOIN).
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
        // 숨김 제외 — 작성자 본인에게는 남는다.
        conditions += "(p.hidden = FALSE OR p.author_id = ?)"
        params += search.viewer.memberId

        // 개별 숨김. hiddenByViewer면 뒤집어 숨긴 글만 남긴다.
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
        // 장소 필터는 spot_id가 아니라 카탈로그로 건다(SPEC-0019).
        search.catalogId?.let {
            conditions += "ps.catalog_id = ?"
            params += it
        }
        // 활성 언어 필터(SPEC-0029).
        conditions += """
            EXISTS (SELECT 1 FROM community.community_spot_catalog_language l
                     WHERE l.catalog_id = ps.catalog_id AND l.language = ?)
        """.trimIndent()
        params += search.language.name
        // 다중 선택은 OR(IN)이다. 비어 있으면 조건을 걸지 않는다.
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

        // 생 SQL은 id와 거리만 정하고, 본문은 엔티티로 읽는다.
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
