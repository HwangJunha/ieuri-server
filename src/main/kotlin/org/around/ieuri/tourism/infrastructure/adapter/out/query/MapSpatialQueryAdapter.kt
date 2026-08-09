package org.around.ieuri.tourism.infrastructure.adapter.out.query

import org.around.ieuri.tourism.application.port.out.query.MapClusterSearch
import org.around.ieuri.tourism.application.port.out.query.MapQueryPort
import org.around.ieuri.tourism.application.port.out.query.MapViewportSearch
import org.around.ieuri.tourism.application.query.MapSpotView
import org.around.ieuri.tourism.domain.vo.MapBounds
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.domain.vo.SpotCluster
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 지도 bbox 조회 전용 어댑터(SPEC-0014 R1~R3). PostGIS 연산자·함수(`&&`·`ST_MakeEnvelope`)를
 * QueryDSL로 표현할 수 없어 이 어댑터에만 생 SQL이 남는다 — `SpotSpatialQueryAdapter`와 같은 선례다.
 *
 * bbox 판정은 `&&`(바운딩박스 겹침)이며 **경계선에 걸친 점을 포함한다**(R1).
 * 캐스팅 방향이 성능을 가르는 지점이라 [BBOX_PREDICATE]의 주석을 함께 읽어야 한다.
 */
@Component
class MapSpatialQueryAdapter(
    private val jdbcTemplate: JdbcTemplate,
) : MapQueryPort {

    /**
     * bbox 안의 개별 마커. 정렬은 id 오름차순이다 — 지도에는 순서 개념이 없지만(POL-019
     * 해당 없음), 상한에 걸려 잘릴 때 매 요청 같은 집합이 나오도록 고정한다.
     */
    @Transactional(readOnly = true)
    override fun findSpotsWithin(query: MapViewportSearch): List<MapSpotView> {
        val categoryCondition = if (query.category != null) "AND category = ?" else ""

        // 파라미터 순서는 SQL 문자열의 ? 등장 순서다.
        val params = buildList<Any> {
            addAll(envelopeParams(query.bounds))
            add(query.language.name)
            query.category?.let { add(it.name) }
            add(query.limit)
        }

        return jdbcTemplate.query(
            """
            SELECT id, title, thumbnail_image_url, category, map_x, map_y
            FROM spot
            WHERE $BBOX_PREDICATE
              AND language = ?
              $categoryCondition
            ORDER BY id
            LIMIT ?
            """.trimIndent(),
            { rs, _ ->
                MapSpotView(
                    id = rs.getLong("id"),
                    title = rs.getString("title"),
                    thumbnailImageUrl = rs.getString("thumbnail_image_url"),
                    category = SpotCategory.valueOf(rs.getString("category")),
                    lng = rs.getDouble("map_x"),
                    lat = rs.getDouble("map_y"),
                )
            },
            *params.toTypedArray(),
        )
    }

    /**
     * 그리드 셀 단위 클러스터(R2). **집계를 DB에서 한다** — 전체를 애플리케이션으로 끌어와
     * 묶으면 저줌에서 상한의 의미가 사라진다.
     *
     * 중심은 셀 중심이 아니라 `AVG(map_x)`·`AVG(map_y)`, 즉 **포함된 spot 좌표의 평균**이다.
     * 셀 중심을 쓰면 마커가 실제 밀집 위치에서 벗어난다.
     *
     * **과반 판정까지 SQL에서 한다.** 카테고리 목록을 애플리케이션으로 올려 세면 셀 하나에
     * 수천 건이 실려 오는데(실측: 한 셀 1,769건), 필요한 것은 최빈 카테고리와 그 개수뿐이다.
     * `mode()`를 쓰지 않는 이유는 개수를 함께 돌려주지 않아 과반인지 알 수 없기 때문이다.
     */
    @Transactional(readOnly = true)
    override fun findClustersWithin(query: MapClusterSearch): List<SpotCluster> {
        val categoryCondition = if (query.category != null) "AND category = ?" else ""

        // 두 CTE가 같은 필터를 쓴다 — 갈리면 셀 개수와 카테고리 개수가 어긋나 과반 판정이 틀린다.
        val sql = """
            WITH cell AS (
                SELECT FLOOR(map_x / ?) AS gx, FLOOR(map_y / ?) AS gy,
                       COUNT(*) AS cnt, AVG(map_x) AS center_lng, AVG(map_y) AS center_lat
                FROM spot
                WHERE $BBOX_PREDICATE AND language = ? $categoryCondition
                GROUP BY 1, 2
            ),
            top_cat AS (
                SELECT DISTINCT ON (gx, gy) gx, gy, category, c
                FROM (
                    SELECT FLOOR(map_x / ?) AS gx, FLOOR(map_y / ?) AS gy,
                           category, COUNT(*) AS c
                    FROM spot
                    WHERE $BBOX_PREDICATE AND language = ? $categoryCondition
                    GROUP BY 1, 2, category
                ) counted
                -- 동점이면 category로 확정한다 — 빼면 같은 요청이 매번 다른 아이콘을 낸다.
                ORDER BY gx, gy, c DESC, category
            )
            SELECT cell.cnt, cell.center_lng, cell.center_lat,
                   -- 과반은 "초과"다: 4건 중 2건은 절반이라 대표가 없다(R2).
                   CASE WHEN top_cat.c * 2 > cell.cnt THEN top_cat.category END AS dominant_category
            FROM cell JOIN top_cat USING (gx, gy)
            ORDER BY cell.cnt DESC, cell.center_lng, cell.center_lat
            LIMIT ?
        """.trimIndent()

        // 파라미터 순서는 ? 등장 순서다 — 두 CTE가 같은 조건을 반복하므로 두 번 넘긴다.
        val params = buildList<Any> {
            repeat(2) {
                add(query.gridSizeInDegrees); add(query.gridSizeInDegrees)
                addAll(envelopeParams(query.bounds))
                add(query.language.name)
                query.category?.let { c -> add(c.name) }
            }
            add(query.limit)
        }

        return jdbcTemplate.query(
            sql,
            { rs, _ ->
                SpotCluster(
                    centerLng = rs.getDouble("center_lng"),
                    centerLat = rs.getDouble("center_lat"),
                    count = rs.getInt("cnt"),
                    dominantCategory = rs.getString("dominant_category")
                        ?.let { SpotCategory.valueOf(it) },
                )
            },
            *params.toTypedArray(),
        )
    }

    /** envelope 파라미터는 (minLng, minLat, maxLng, maxLat) 순서다 — 경도가 먼저다. */
    private fun envelopeParams(bounds: MapBounds): List<Any> =
        listOf(bounds.minLng, bounds.minLat, bounds.maxLng, bounds.maxLat)

    private companion object {
        /**
         * bbox 교차 판정. `ST_MakeEnvelope`은 **(minLng, minLat, maxLng, maxLat)** 순서이며
         * 경도가 먼저다 — 위경도 순으로 넣으면 조용히 틀린다.
         *
         * **`&&`를 쓰고 envelope을 geography로 캐스팅한다.** `idx_spot_location`이 geography
         * GiST 인덱스라, `ST_Intersects(location::geometry, ...)`로 쓰면 컬럼 쪽 캐스팅이
         * 인덱스를 무력화해 전 행 필터가 된다(실측 비용 291,650 → 1,112). 캐스팅 방향이
         * 성능을 가르는 지점이다.
         *
         * `&&`는 바운딩박스 겹침이라 경계선에 걸친 점을 포함한다(R1). 점 데이터에서는
         * `ST_Intersects`와 결과가 같다 — 점의 바운딩박스가 점 자신이기 때문이다.
         */
        const val BBOX_PREDICATE =
            "location && ST_MakeEnvelope(?, ?, ?, ?, 4326)::geography"
    }
}
