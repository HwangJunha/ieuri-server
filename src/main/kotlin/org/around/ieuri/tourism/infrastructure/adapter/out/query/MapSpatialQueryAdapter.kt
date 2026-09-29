package org.around.ieuri.tourism.infrastructure.adapter.out.query

import org.around.ieuri.tourism.application.port.out.query.MapCandidateSearch
import org.around.ieuri.tourism.application.port.out.query.MapCandidateView
import org.around.ieuri.tourism.application.port.out.query.MapQueryPort
import org.around.ieuri.tourism.application.port.out.query.MapViewportSearch
import org.around.ieuri.tourism.application.query.MapSpotView
import org.around.ieuri.tourism.domain.vo.MapBounds
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 지도 bbox 조회 어댑터(SPEC-0014 R1·R3). PostGIS 연산자·함수 때문에 생 SQL이다.
 * - bbox 판정은 경계선에 걸친 점을 포함한다(R1). 캐스팅 방향은 [BBOX_PREDICATE] 참조.
 * - `spot`을 읽지 않는다(SPEC-0019). 마커 id는 `spot_catalog.id`다.
 */
@Component
class MapSpatialQueryAdapter(
    private val jdbcTemplate: JdbcTemplate,
) : MapQueryPort {

    /** bbox 안의 개별 마커. id 오름차순 — 상한에 걸려 잘릴 때 매 요청 같은 집합이 나온다. */
    @Transactional(readOnly = true)
    override fun findSpotsWithin(query: MapViewportSearch): List<MapSpotView> {
        // 다중 선택은 OR(SPEC-0020). 비어 있으면 조건을 걸지 않는다.
        val categoryCodes = query.categories.codes()
        val categoryCondition =
            if (query.categories.isUnfiltered) {
                ""
            } else {
                "AND cat.category IN (${categoryCodes.joinToString(",") { "?" }})"
            }

        // 파라미터 순서는 `?` 등장 순서다 — 언어가 JOIN 조건에 있어 bbox보다 앞선다.
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

    /**
     * bbox 안의 추천 후보(SPEC-0036). 중심점에서 가까운 순 상한 N건이며 최종 순서는 호출자가 점수로 정한다.
     * - 정렬은 `<->`(top-N heapsort), 표시용 거리는 `ST_Distance`로 따로 계산한다.
     * - 제외 id는 배열 한 개로 바인딩한다.
     */
    @Transactional(readOnly = true)
    override fun findRecommendationCandidatesWithin(query: MapCandidateSearch): List<MapCandidateView> {
        val categoryCodes = query.categories.codes()
        val categoryCondition =
            if (query.categories.isUnfiltered) {
                ""
            } else {
                "AND cat.category IN (${categoryCodes.joinToString(",") { "?" }})"
            }
        // 제외 목록이 비면 조건을 뺀다.
        val exclusionCondition =
            if (query.excludedSpotIds.isEmpty()) {
                ""
            } else {
                "AND NOT (cat.id = ANY (?))"
            }

        val center = query.bounds.center
        // 파라미터 순서는 `?` 등장 순서다. 중심점은 CTE로 한 번만 바인딩한다.
        val params = buildList<Any> {
            add(center.longitude)
            add(center.latitude)
            add(query.language.name)
            addAll(envelopeParams(query.bounds))
            addAll(categoryCodes)
            if (query.excludedSpotIds.isNotEmpty()) {
                add(query.excludedSpotIds.toLongArray())
            }
            add(query.limit)
        }

        return jdbcTemplate.query(
            """
            WITH c AS (SELECT $CENTER AS center)
            SELECT cat.id AS id,
                   t.title, cat.thumbnail_image_url, cat.category, cat.map_x, cat.map_y,
                   ST_Distance(cat.location, c.center) AS distance_meters
            FROM spot_catalog cat
            JOIN spot_catalog_translation t ON t.catalog_id = cat.id AND t.language = ? AND t.visible
            -- **CROSS JOIN이 JOIN 뒤에 와야 한다.** `FROM cat, c JOIN t ON …`으로 쓰면 ON 절이
            -- cat을 볼 수 없어 "invalid reference to FROM-clause entry"로 거절된다.
            CROSS JOIN c
            WHERE $BBOX_PREDICATE
              $categoryCondition
              $exclusionCondition
            ORDER BY cat.location <-> c.center, cat.id
            LIMIT ?
            """.trimIndent(),
            { rs, _ ->
                MapCandidateView(
                    id = rs.getLong("id"),
                    title = rs.getString("title"),
                    thumbnailImageUrl = rs.getString("thumbnail_image_url"),
                    category = SpotCategory(rs.getString("category")),
                    lng = rs.getDouble("map_x"),
                    lat = rs.getDouble("map_y"),
                    distanceMeters = rs.getDouble("distance_meters"),
                )
            },
            *params.toTypedArray(),
        )
    }

    /** envelope 파라미터는 (minLng, minLat, maxLng, maxLat) 순서다 — 경도가 먼저다. */
    private fun envelopeParams(bounds: MapBounds): List<Any> = listOf(bounds.minLng, bounds.minLat, bounds.maxLng, bounds.maxLat)

    private companion object {
        /**
         * bbox 교차 판정. `ST_MakeEnvelope`은 (minLng, minLat, maxLng, maxLat) 순서다 — 위경도 순이면 조용히 틀린다.
         * - envelope을 geography로 캐스팅해야 GiST 인덱스를 탄다. 컬럼을 geometry로 캐스팅하면 전 행 필터가 된다.
         * - 경계선에 걸친 점을 포함한다(R1). 좌표가 없는 카탈로그는 빠진다.
         */
        const val BBOX_PREDICATE =
            "cat.location && ST_MakeEnvelope(?, ?, ?, ?, 4326)::geography"

        /** 뷰포트 중심점(SPEC-0036). ST_MakePoint는 (경도, 위도) 순서다 — 뒤집으면 조용히 틀린다. */
        const val CENTER = "ST_MakePoint(?, ?)::geography"
    }
}
