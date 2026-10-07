package org.around.ieuri.tourism.adapter.persistence.query

import org.around.ieuri.tourism.application.catalog.provided.dto.PopularSpotView
import org.around.ieuri.tourism.application.catalog.provided.dto.SpotView
import org.around.ieuri.tourism.application.catalog.required.PopularSpotQueryPort
import org.around.ieuri.tourism.application.catalog.required.PopularSpotSearch
import org.around.ieuri.tourism.domain.catalog.ReactionType
import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * 인기 spot 조회 어댑터(SPEC-0013 R18). LIKE를 카탈로그 단위로, 활성 언어 안에서, 기간 제한 없이 집계한다.
 * - 검색 노드를 봐서 Exposed 밖의 생 SQL이다. 좋아요 하한은 집계 후 HAVING으로 적용한다(AC-18.2).
 * - `spot`을 읽지 않는다(SPEC-0019). 제목·주소는 번역, 나머지는 카탈로그에서 온다.
 * - 검색 노드를 본다 — 방금 누른 좋아요는 복제 지연만큼 늦게 반영된다.
 */
@Component
class PopularSpotQueryAdapter(
    @Qualifier("searchJdbcTemplate") private val jdbcTemplate: JdbcTemplate,
) : PopularSpotQueryPort {

    /**
     * 좋아요 내림차순 + id 오름차순. `limit + 1`건을 읽어 호출자가 다음 페이지 존재를 판정한다.
     * - seek을 한 줄로 쓰려고 like_count 부호를 뒤집는다. 커서에는 원래 값이 실린다.
     */
    override fun findPopular(query: PopularSpotSearch): List<PopularSpotView> {
        val (where, whereParams) = whereClause(query)
        // 파라미터는 `?` 등장 순서다: SELECT의 거리 중심 → 두 JOIN → WHERE → HAVING.
        val params = (listOf<Any>(query.center.longitude, query.center.latitude) + whereParams).toMutableList()
        params += query.minLikeCount // HAVING

        val seek = if (query.after != null) {
            params += -query.after.likeCount
            params += query.after.spotId
            "WHERE (-like_count, id) > (?, ?)"
        } else {
            ""
        }
        params += query.limit + 1

        return jdbcTemplate.query(
            """
            SELECT * FROM (
                SELECT cat.id, t.title, t.address, cat.thumbnail_image_url, cat.map_x, cat.map_y,
                       cat.category, cat.copyright,
                       ST_Distance(cat.location, $CENTER) AS distance_meters,
                       COUNT(r.id) AS like_count
                FROM spot_catalog cat
                -- **카탈로그 축이되 활성 언어 안에서만 집계한다**(SPEC-0029 AC-13.5).
                -- `r.language`가 빠지면 다른 언어에서 누른 좋아요가 이 언어의 순위를 올린다.
                JOIN spot_reaction r ON r.catalog_id = cat.id AND r.reaction = ? AND r.language = ?
                -- **카탈로그를 1행으로 접는 역할은 이 조인이 맡는다.** 예전에는 `s.language = ?`가
                -- 그 역할이었다 — 그 조건이 없으면 같은 카탈로그의 4개 언어 행이 각각 같은 집계를
                -- 받아 결과가 4배로 늘어났다. 이제는 `t.language = ?` + `UNIQUE(catalog_id,
                -- language)`가 카탈로그당 정확히 1행을 보장한다. 동시에 의미가 바뀌었다:
                -- "그 언어의 spot 행이 있는가"가 아니라 **"그 언어의 제목이 있는가"**다.
                JOIN spot_catalog_translation t ON t.catalog_id = cat.id AND t.language = ? AND t.visible
                WHERE $where
                GROUP BY cat.id, t.title, t.address
                HAVING COUNT(r.id) >= ?
            ) ranked
            $seek
            ORDER BY like_count DESC, id ASC
            LIMIT ?
            """.trimIndent(),
            { rs, _ ->
                val copyright = CopyrightType.valueOf(rs.getString("copyright"))
                PopularSpotView(
                    spot = SpotView(
                        // 클라이언트에 나가는 id는 카탈로그 id다(SPEC-0019).
                        id = rs.getLong("id"),
                        title = rs.getString("title"),
                        address = rs.getString("address"),
                        thumbnailImageUrl = rs.getString("thumbnail_image_url"),
                        distanceMeters = rs.getDouble("distance_meters"),
                        category = SpotCategory(rs.getString("category")),
                        copyright = copyright,
                        attributionText = copyright.attributionText,
                        mapX = rs.getDouble("map_x"),
                        mapY = rs.getDouble("map_y"),
                    ),
                    likeCount = rs.getLong("like_count"),
                )
            },
            *params.toTypedArray(),
        )
    }

    /**
     * 조건과, `?` 등장 순서대로 담은 파라미터: 두 JOIN 조건 → WHERE. 거리 중심과 HAVING 하한은 호출자가 붙인다.
     */
    private fun whereClause(query: PopularSpotSearch): Pair<String, List<Any>> {
        // 카테고리 없는 카탈로그는 빠진다. 좌표 없는 카탈로그는 ST_DWithin에서 빠진다.
        val conditions = mutableListOf(
            "ST_DWithin(cat.location, $CENTER, ?)",
        )
        val params = mutableListOf<Any>(
            ReactionType.LIKE.name, // spot_reaction JOIN: reaction
            query.language.name, // spot_reaction JOIN: language
            query.language.name, // translation JOIN 조건
            query.center.longitude,
            query.center.latitude,
            query.radiusMeters.toDouble(),
        )

        // 다중 선택은 OR(SPEC-0020). 비어 있으면 조건을 걸지 않는다.
        if (!query.categories.isUnfiltered) {
            val codes = query.categories.codes()
            conditions += "cat.category IN (${codes.joinToString(",") { "?" }})"
            params.addAll(codes)
        }
        return conditions.joinToString(" AND ") to params
    }

    private companion object {
        /** ST_MakePoint는 (경도, 위도) 순서다 — 뒤집으면 조용히 틀린다. */
        const val CENTER = "ST_MakePoint(?, ?)::geography"
    }
}
