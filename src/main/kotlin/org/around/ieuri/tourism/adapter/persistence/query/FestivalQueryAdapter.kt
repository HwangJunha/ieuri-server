package org.around.ieuri.tourism.adapter.persistence.query

import org.around.ieuri.tourism.application.shared.provided.dto.FestivalView
import org.around.ieuri.tourism.application.shared.required.FestivalQueryPort
import org.around.ieuri.tourism.application.shared.required.OngoingFestivalSearch
import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.around.ieuri.tourism.domain.shared.SpotCategory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.Date

/**
 * 진행 중 축제 조회 어댑터(SPEC-0013 R17). PostGIS 거리 계산·정렬 때문에 생 SQL이다.
 * - `spot`·`spot_festival`을 읽지 않는다(SPEC-0019). 기간은 `spot_catalog_festival`에서 온다.
 * - 언어 격리는 번역 조인의 `t.language = ?`가 전담한다.
 * - 검색 노드를 본다(홈 세 섹션이 같은 노드).
 * - 날짜 필터를 조회 시점에 적용한다 — 배치의 종료 축제 정리에 기대지 않는다.
 */
@Component
class FestivalQueryAdapter(
    @Qualifier("searchJdbcTemplate") private val jdbcTemplate: JdbcTemplate,
) : FestivalQueryPort {

    /** 거리 오름차순 + id 오름차순. `limit + 1`건을 읽어 호출자가 다음 페이지 존재를 판정한다. */
    override fun findOngoing(query: OngoingFestivalSearch): List<FestivalView> {
        val (where, whereParams) = whereClause(query)
        val params = whereParams.toMutableList()

        val seek = if (query.after != null) {
            params += query.after.distanceMeters
            params += query.after.spotId
            "WHERE (distance_meters, id) > (?, ?)"
        } else {
            ""
        }
        params += query.limit + 1

        return jdbcTemplate.query(
            """
            WITH c AS (SELECT $CENTER AS center)
            SELECT * FROM (
                SELECT cat.id, t.title, t.address, cat.thumbnail_image_url, cat.map_x, cat.map_y,
                       cat.category, cat.copyright,
                       f.event_start_date, f.event_end_date,
                       ST_Distance(cat.location, c.center) AS distance_meters
                FROM spot_catalog cat
                -- PK가 catalog_id라 이 조인 자체가 "축제인 카탈로그만"을 뜻한다.
                JOIN spot_catalog_festival f ON f.catalog_id = cat.id
                JOIN spot_catalog_translation t ON t.catalog_id = cat.id AND t.language = ? AND t.visible
                CROSS JOIN c
                WHERE $where
            ) ranked
            $seek
            ORDER BY distance_meters ASC, id ASC
            LIMIT ?
            """.trimIndent(),
            { rs, _ ->
                val copyright = CopyrightType.valueOf(rs.getString("copyright"))
                FestivalView(
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
                    eventStartDate = rs.getDate("event_start_date").toLocalDate(),
                    eventEndDate = rs.getDate("event_end_date").toLocalDate(),
                )
            },
            *params.toTypedArray(),
        )
    }

    /**
     * 조건과 `?` 등장 순서대로 담은 파라미터(CTE center가 앞선다).
     * - 기간 판정은 경계를 포함한다(AC-17.3). '오늘'은 서비스가 넘긴 값이며 `CURRENT_DATE`를 쓰지 않는다.
     */
    private fun whereClause(query: OngoingFestivalSearch): Pair<String, List<Any>> {
        // 카테고리 없는 카탈로그는 빠진다. 좌표 없는 카탈로그는 ST_DWithin에서 빠진다.
        val conditions = listOf(
            "f.event_start_date <= ?",
            "f.event_end_date >= ?",
            "ST_DWithin(cat.location, c.center, ?)",
        )
        val params = listOf<Any>(
            query.center.longitude,
            query.center.latitude, // CTE의 center
            query.language.name, // translation JOIN 조건
            Date.valueOf(query.asOf),
            Date.valueOf(query.asOf),
            query.radiusMeters.toDouble(),
        )
        return conditions.joinToString(" AND ") to params
    }

    private companion object {
        /** ST_MakePoint는 (경도, 위도) 순서다 — 뒤집으면 조용히 틀린다. */
        const val CENTER = "ST_MakePoint(?, ?)::geography"
    }
}
