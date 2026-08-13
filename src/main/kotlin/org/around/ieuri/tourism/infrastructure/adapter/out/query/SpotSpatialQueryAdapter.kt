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
     * 중심에서 반경 안에 있는 spot의 id만. 다른 도메인이 반경 조건을 필요로 할 때 쓰는 진입점이며,
     * `spot` 테이블을 아는 코드가 tourism 밖으로 새지 않게 한다.
     *
     * **중심에서 가까운 순으로 돌려준다.** id 순으로 주면 상한에 걸릴 때 가까운 spot이 잘려
     * 나가고 먼 spot이 남는다 — 반경 5km 안에 있는데도 내 앞 공원 대신 저 멀리 궁이 보이는 일이
     * 그래서 생긴다. `<->`는 GiST 인덱스를 타는 KNN 연산자라 정렬에 전 행 계산이 필요 없다.
     *
     * [categoryCode]를 주면 그 카테고리만. **null이면 거르지 않는다.** 도메인 enum이 아니라
     * 문자열로 받는 이유는 다른 도메인이 tourism의 SpotCategory를 import하지 않게 하려는 것이다.
     *
     * [limit]으로 상한을 둔다 — 100km 반경이면 수만 건이 나올 수 있고, 호출자가 이 결과를
     * `IN` 절에 넣으면 파라미터 수와 실행 계획이 함께 무너진다. 상한에 걸리면 조용히 잘리므로
     * 호출자는 그것을 감안해야 한다(가까운 순이라 잘리는 것은 먼 쪽이다).
     */
    fun findIdsWithin(
        longitude: Double,
        latitude: Double,
        radiusMeters: Int,
        limit: Int,
        categoryCode: String? = null,
    ): List<Long> {
        // 카테고리는 선택이다. 준 경우에만 조건과 파라미터가 함께 붙어야 순서가 어긋나지 않는다.
        val categoryCondition = if (categoryCode != null) "AND category = ?" else ""

        // **파라미터 순서는 SQL 문자열의 ? 등장 순서다.** CENTER 하나가 ?를 둘(경도·위도) 쓰며,
        // WHERE에 한 번, ORDER BY에 한 번 등장하므로 좌표를 두 번 넘긴다.
        val params = buildList<Any> {
            add(longitude); add(latitude)          // WHERE의 CENTER
            add(radiusMeters.toDouble())           // ST_DWithin의 반경
            if (categoryCode != null) add(categoryCode)
            add(longitude); add(latitude)          // ORDER BY의 CENTER
            add(limit)
        }

        return jdbcTemplate.query(
            """
            SELECT id FROM spot
            WHERE $ACTIVE_ONLY
              AND ST_DWithin(location, $CENTER, ?)
              $categoryCondition
            ORDER BY location <-> $CENTER, id
            LIMIT ?
            """.trimIndent(),
            { rs, _ -> rs.getLong("id") },
            *params.toTypedArray(),
        )
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
        // `active`는 파라미터가 아니라 상수 조건이라 순서에 끼어들지 않는다(SPEC-0018 R2).
        val conditions = mutableListOf(ACTIVE_ONLY, "language = ?", "ST_DWithin(location, c.center, ?)")
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

        /** 비관광 spot 제외(SPEC-0018 R2). 모든 조회 경로가 이 조건을 함께 건다. */
        const val ACTIVE_ONLY = "active = TRUE"
    }
}
