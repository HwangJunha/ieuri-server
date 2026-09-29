package org.around.ieuri.tourism.infrastructure.adapter.out.query

import org.around.ieuri.tourism.application.port.out.query.NearbySpotSearch
import org.around.ieuri.tourism.application.port.out.query.SpotSearchResult
import org.around.ieuri.tourism.application.port.out.query.SpotSpatialQueryPort
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 반경 조회 어댑터. PostGIS 함수를 QueryDSL로 표현할 수 없어 생 SQL이다.
 * - 조회와 카운트가 같은 조건이어야 한다 — 갈리면 hasNext와 totalCount가 어긋난다.
 * - `spot`을 읽지 않고 번역·카탈로그만 본다(SPEC-0019).
 * - 거리 정렬은 번역 행 단독으로 하고, 썸네일·저작권만 `LIMIT` 뒤에 카탈로그와 조인한다(SPEC-0038).
 */
@Component
class SpotSpatialQueryAdapter(
    private val jdbcTemplate: JdbcTemplate,
) : SpotSpatialQueryPort {

    /** 반경 안 관광지 한 페이지. 거리 오름차순, 등거리는 id 오름차순이다. */
    @Transactional(readOnly = true)
    override fun searchNearby(query: NearbySpotSearch): List<SpotSearchResult> {
        val (where, params) = whereClause(query)
        // 중심 좌표는 CTE로 한 번만 바인딩한다.
        // 카탈로그 조인은 정렬·LIMIT 뒤다 — ranked와 바깥의 ORDER BY는 같은 키여야 한다(POL-019·055).
        val sql = """
            WITH c AS (SELECT $CENTER AS center),
                 ranked AS (
                     SELECT $RANKED_COLUMNS,
                            ST_Distance(t.location, c.center) AS distance_meters
                     FROM spot_catalog_translation t, c
                     WHERE $where
                     ORDER BY ST_Distance(t.location, c.center), t.catalog_id
                     LIMIT ? OFFSET ?
                 )
            SELECT $CARD_COLUMNS
            FROM ranked r JOIN spot_catalog cat ON cat.id = r.catalog_id
            ORDER BY r.distance_meters, r.catalog_id
        """.trimIndent()

        return jdbcTemplate.query(
            sql,
            { rs, _ ->
                SpotSearchResult(
                    // 클라이언트에 나가는 id는 카탈로그 id다(SPEC-0019).
                    spotId = rs.getLong("id"),
                    title = rs.getString("title"),
                    address = rs.getString("address"),
                    thumbnailImageUrl = rs.getString("thumbnail_image_url"),
                    mapX = rs.getDouble("map_x"),
                    mapY = rs.getDouble("map_y"),
                    category = SpotCategory(rs.getString("category")),
                    // 컬럼에 enum 이름이 들어 있으므로 valueOf다.
                    copyright = CopyrightType.valueOf(rs.getString("copyright")),
                    distanceMeters = rs.getDouble("distance_meters"),
                )
            },
            *(params + query.limit + query.offset).toTypedArray(),
        )
    }

    /** [searchNearby]와 같은 조건에 걸리는 전체 건수. */
    @Transactional(readOnly = true)
    override fun countNearby(query: NearbySpotSearch): Int {
        val (where, params) = whereClause(query)
        // 카탈로그를 조인하지 않아도 수가 같다 — UNIQUE(catalog_id, language)와 catalog_id NOT NULL FK에 기댄다.
        // FK를 지우면 고아 행이 이 카운트에만 더해진다.
        return jdbcTemplate.queryForObject(
            "WITH c AS (SELECT $CENTER AS center) SELECT COUNT(*) FROM spot_catalog_translation t, c WHERE $where",
            Int::class.java,
            *params.toTypedArray(),
        ) ?: 0
    }

    /**
     * 조회와 카운트가 함께 쓰는 조건과 파라미터.
     * - 반경 필터는 `ST_DWithin`이어야 한다 — `ST_Distance(...) <= radius`는 GiST 인덱스를 못 탄다.
     */
    private fun whereClause(query: NearbySpotSearch): Pair<String, List<Any>> {
        // 파라미터 순서: CTE의 center(경도, 위도)가 먼저, 그다음 WHERE 조건 순서다.
        // - 번역의 언어로 거른다(SPEC-0019 R-9). 카테고리가 없는 카탈로그는 빠진다.
        val conditions = mutableListOf(
            "t.visible",
            "t.language = ?",
            "ST_DWithin(t.location, c.center, ?)",
        )
        val params = mutableListOf<Any>(
            query.center.longitude,
            query.center.latitude,
            query.language.name,
            query.radiusMeters.toDouble(),
        )

        // 다중 선택은 OR(SPEC-0020). 비어 있으면 조건을 걸지 않는다.
        if (!query.categories.isUnfiltered) {
            val codes = query.categories.codes()
            conditions += "t.category IN (${codes.joinToString(",") { "?" }})"
            params.addAll(codes)
        }
        if (query.excludedSpotIds.isNotEmpty()) {
            // 이미 반응한 카탈로그는 DB 단계에서 제외한다 — 조회 후 거르면 페이지가 size보다 작아진다.
            // id 목록은 배열 한 개로 바인딩한다.
            conditions += "t.catalog_id <> ALL (?)"
            params += query.excludedSpotIds.toLongArray()
        }
        return conditions.joinToString(" AND ") to params
    }

    private companion object {
        /** 요청 좌표를 geography 점으로. ST_MakePoint는 (경도, 위도) 순서다 — 뒤집으면 조용히 틀린다. */
        const val CENTER = "ST_MakePoint(?, ?)::geography"

        /** 정렬 단계가 번역 행에서 읽는 컬럼. 카탈로그 컬럼을 더하면 조인이 `LIMIT` 앞으로 돌아간다. */
        const val RANKED_COLUMNS = "t.catalog_id, t.title, t.address, t.map_x, t.map_y, t.category"

        /** 카드가 읽는 컬럼. 썸네일·저작권만 카탈로그에서 온다. id는 카탈로그 id다. */
        const val CARD_COLUMNS =
            "r.catalog_id AS id, r.title, r.address, cat.thumbnail_image_url, " +
                "r.map_x, r.map_y, r.category, cat.copyright, r.distance_meters"
    }
}
