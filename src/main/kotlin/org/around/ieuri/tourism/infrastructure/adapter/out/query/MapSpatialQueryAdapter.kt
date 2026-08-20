package org.around.ieuri.tourism.infrastructure.adapter.out.query

import org.around.ieuri.tourism.application.port.out.query.MapQueryPort
import org.around.ieuri.tourism.application.port.out.query.MapViewportSearch
import org.around.ieuri.tourism.application.query.MapSpotView
import org.around.ieuri.tourism.domain.vo.MapBounds
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 지도 bbox 조회 전용 어댑터(SPEC-0014 R1·R3). PostGIS 연산자·함수(`&&`·`ST_MakeEnvelope`)를
 * QueryDSL로 표현할 수 없어 이 어댑터에만 생 SQL이 남는다 — `SpotSpatialQueryAdapter`와 같은 선례다.
 *
 * bbox 판정은 `&&`(바운딩박스 겹침)이며 **경계선에 걸친 점을 포함한다**(R1).
 * 캐스팅 방향이 성능을 가르는 지점이라 [BBOX_PREDICATE]의 주석을 함께 읽어야 한다.
 *
 * **`spot`을 읽지 않는다**(SPEC-0019). 카탈로그와 번역만 보므로 그 언어의 spot 행이 없어도
 * 번역이 있으면 지도에 나타나며, 마커 id는 `spot_catalog.id`다.
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
        // 다중 선택은 OR이며 IN으로 표현한다(SPEC-0020). 비어 있으면 조건 자체를 걸지 않는다.
        val categoryCodes = query.categories.codes()
        val categoryCondition =
            if (query.categories.isUnfiltered) ""
            else "AND cat.category IN (${categoryCodes.joinToString(",") { "?" }})"

        // **파라미터 순서는 SQL 문자열의 ? 등장 순서다.** 언어가 JOIN 조건에 있어 bbox보다 앞선다.
        val params = buildList<Any> {
            add(query.language.name)
            addAll(envelopeParams(query.bounds))
            addAll(categoryCodes)
            add(query.limit)
        }

        return jdbcTemplate.query(
            """
            SELECT cat.id AS id,
                   t.title, cat.thumbnail_image_url, cat.category, cat.map_x, cat.map_y
            FROM spot_catalog cat
            -- **제목만 번역에서 온다.** 좌표·썸네일·분류는 카탈로그가 확정한 표준값이다.
            -- `UNIQUE(catalog_id, language)`가 있어 이 조인은 카탈로그당 최대 1행이다.
            JOIN spot_catalog_translation t ON t.catalog_id = cat.id AND t.language = ? AND t.visible
            WHERE $BBOX_PREDICATE
              -- 카테고리 없는 카탈로그는 마커를 그릴 수 없다 — 도메인 enum에 미분류 값이 없다.
              $categoryCondition
            ORDER BY cat.id
            LIMIT ?
            """.trimIndent(),
            { rs, _ ->
                MapSpotView(
                    id = rs.getLong("id"),
                    title = rs.getString("title"),
                    thumbnailImageUrl = rs.getString("thumbnail_image_url"),
                    category = SpotCategory(rs.getString("category")),
                    lng = rs.getDouble("map_x"),
                    lat = rs.getDouble("map_y"),
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
         * **`&&`를 쓰고 envelope을 geography로 캐스팅한다.** `idx_spot_catalog_location`이
         * geography GiST 인덱스라, `ST_Intersects(location::geometry, ...)`로 쓰면 컬럼 쪽
         * 캐스팅이 인덱스를 무력화해 전 행 필터가 된다(실측 비용 291,650 → 1,112). 캐스팅
         * 방향이 성능을 가르는 지점이다.
         *
         * `&&`는 바운딩박스 겹침이라 경계선에 걸친 점을 포함한다(R1). 점 데이터에서는
         * `ST_Intersects`와 결과가 같다 — 점의 바운딩박스가 점 자신이기 때문이다.
         *
         * 좌표가 없는 카탈로그(location null)는 이 조건에서 자연히 빠진다.
         */
        const val BBOX_PREDICATE =
            "cat.location && ST_MakeEnvelope(?, ?, ?, ?, 4326)::geography"
    }
}
