package org.around.ieuri.tourism.infrastructure.adapter.out.query

import org.around.ieuri.tourism.application.port.out.query.NearbySpotSearch
import org.around.ieuri.tourism.application.port.out.query.SpotSearchResult
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * 반경 조회 전용 어댑터. PostGIS 함수(`ST_DWithin`·`ST_Distance`·`ST_MakePoint`)를 QueryDSL로
 * 표현할 수 없어 이 어댑터에만 생 SQL이 남는다.
 *
 * 조회와 카운트가 같은 조건을 써야 하므로 둘을 한 클래스에 두어 눈으로 대조할 수 있게 한다 —
 * 조건이 갈리면 hasNext와 totalCount가 어긋난다.
 */
@Component
class SpotSpatialQueryAdapter(
    private val jdbcTemplate: JdbcTemplate,
) {

    /**
     * 중심에서 반경 안의 관광지 한 페이지. 거리 오름차순, 등거리는 id 오름차순이다 —
     * `ORDER BY`의 `id`를 빼면 등거리 행의 순서가 요청마다 달라져 항목이 중복·누락된다.
     */
    fun searchNearby(query: NearbySpotSearch): List<SpotSearchResult> {
        val (where, params) = whereClause(query)
        // 중심 좌표를 CTE로 한 번만 바인딩한다. SELECT·WHERE·ORDER BY 세 곳에서 쓰이는데
        // 그때마다 ST_MakePoint(?, ?)를 반복하면 위치 파라미터가 6개로 늘어 순서를 맞추기 어렵다.
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

    /** [searchNearby]와 같은 조건에 걸리는 전체 건수. */
    fun countNearby(query: NearbySpotSearch): Int {
        val (where, params) = whereClause(query)
        return jdbcTemplate.queryForObject(
            "WITH c AS (SELECT $CENTER AS center) SELECT COUNT(*) FROM spot, c WHERE $where",
            Int::class.java,
            *params.toTypedArray(),
        ) ?: 0
    }

    /**
     * 조회와 카운트가 **같은 조건**을 쓰도록 한 곳에서 만든다. 둘이 갈리면 hasNext와 totalCount가
     * 서로 어긋난다.
     *
     * 반경 필터는 반드시 `ST_DWithin`이다 — `ST_Distance(...) <= radius`로 쓰면 전 행을 계산해
     * GiST 인덱스를 타지 못한다.
     */
    private fun whereClause(query: NearbySpotSearch): Pair<String, List<Any>> {
        // 파라미터 순서: CTE의 center(경도, 위도)가 먼저 오고, 그다음 WHERE 조건 순서다.
        val conditions = mutableListOf("language = ?", "ST_DWithin(location, c.center, ?)")
        val params = mutableListOf<Any>(
            query.center.longitude, query.center.latitude,
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
}
