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
 *
 * **`spot`을 읽지 않는다**(SPEC-0019). 카탈로그와 번역만 본다 — `spot.language`로 거르면 그
 * 언어의 spot 행이 없는 장소가 목록에서 통째로 빠지는데, 이 스펙이 번역으로 채우는 대상이
 * 정확히 그 장소들이다. 제목·주소는 번역에서, 좌표·썸네일·분류·저작권은 카탈로그가 확정해 둔
 * 표준값에서 온다.
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
            SELECT $CARD_COLUMNS,
                   ST_Distance(cat.location, c.center) AS distance_meters
            FROM $CARD_SOURCE, c
            WHERE $where
            ORDER BY ST_Distance(cat.location, c.center), cat.id
            LIMIT ? OFFSET ?
        """.trimIndent()

        return jdbcTemplate.query(
            sql,
            { rs, _ ->
                SpotSearchResult(
                    // **클라이언트에 나가는 id는 카탈로그 id다**(SPEC-0019) — 목록의 단위가
                    // 언어별 spot 행이 아니라 장소이기 때문이다.
                    spotId = rs.getLong("id"),
                    title = rs.getString("title"),
                    address = rs.getString("address"),
                    // 카탈로그의 썸네일은 nullable이다. 카드 자리를 비워 두는 편이 조회를 통째로
                    // 실패시키는 것보다 낫다.
                    thumbnailImageUrl = rs.getString("thumbnail_image_url").orEmpty(),
                    mapX = rs.getDouble("map_x"),
                    mapY = rs.getDouble("map_y"),
                    category = SpotCategory.valueOf(rs.getString("category")),
                    // copyright는 nullable이며 "미제공"이 곧 UNSPECIFIED다. 컬럼에는 enum 이름이
                    // 그대로 들어 있으므로 코드 문자열 변환(fromCode)이 아니라 valueOf다.
                    copyright = rs.getString("copyright")
                        ?.let { CopyrightType.valueOf(it) }
                        ?: CopyrightType.UNSPECIFIED,
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
            "WITH c AS (SELECT $CENTER AS center) SELECT COUNT(*) FROM $CARD_SOURCE, c WHERE $where",
            Int::class.java,
            *params.toTypedArray(),
        ) ?: 0
    }

    /**
     * 중심에서 반경 안에 있는 **카탈로그 id**만. 다른 도메인이 반경 조건을 필요로 할 때 쓰는
     * 진입점이며, tourism의 테이블을 아는 코드가 밖으로 새지 않게 한다.
     *
     * **돌려주는 것은 `spot_catalog.id`다**(SPEC-0019 R-12) — 다른 도메인이 장소를 가리킬 때
     * 저장하는 축이 카탈로그이므로(`community_post_spot.catalog_id`), 여기서 언어별 `spot.id`를
     * 주면 호출자가 그것을 다시 카탈로그로 환산해야 한다.
     *
     * **번역을 조인하지 않는다.** 표시값이 아니라 id만 돌려주므로 언어가 관여할 자리가 없다.
     * 좌표가 없는 카탈로그는 애초에 반경 조건에 걸리지 않는다(location이 null이면 ST_DWithin이
     * 참이 되지 않는다).
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
            SELECT id FROM spot_catalog
            WHERE ST_DWithin(location, $CENTER, ?)
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
        // 파라미터 순서: CTE의 center(경도, 위도)가 먼저 오고, 그다음 조인·WHERE 조건 순서다.
        // **번역 기준으로 거른다**(SPEC-0019 R-9) — `t.language`는 spot 행의 언어가 아니라
        // 번역의 언어다. 번역이 채워진 카탈로그는 그 언어의 spot 행이 없어도 목록에 나온다.
        // 그것이 이 스펙 목적의 절반이다.
        //
        // **좌표가 없는 카탈로그는 빠진다.** `ST_DWithin`이 null location에 참을 돌려주지 않아
        // 자연히 걸러지지만, 상류 결손 317건이 여기서 사라진다는 사실 자체는 기록해 둔다.
        //
        // **카테고리가 없는 카탈로그도 빠진다.** 카드가 카테고리를 필수로 요구하는데(도메인 enum에
        // 미분류 값이 없다) 임의의 기본값을 만들면 잘못된 분류로 노출된다.
        val conditions = mutableListOf(
            "t.language = ?",
            "cat.category IS NOT NULL",
            "ST_DWithin(cat.location, c.center, ?)",
        )
        val params = mutableListOf<Any>(
            query.center.longitude, query.center.latitude,
            query.language.name,
            query.radiusMeters.toDouble(),
        )

        query.category?.let {
            conditions += "cat.category = ?"
            params += it.name
        }
        if (query.excludedSpotIds.isNotEmpty()) {
            // 이미 반응한 항목은 DB 단계에서 제외한다 — 조회 후 걸러내면 페이지가 size보다 작아진다.
            // **제외 대상은 카탈로그 id다** — 목록이 카탈로그 id를 내보내므로 호출자가 되돌려
            // 주는 것도 같은 축이다.
            conditions += "cat.id NOT IN (${query.excludedSpotIds.joinToString(",") { "?" }})"
            params.addAll(query.excludedSpotIds)
        }
        return conditions.joinToString(" AND ") to params
    }

    private companion object {
        /** 요청 좌표를 geography 점으로. ST_MakePoint는 (경도, 위도) 순서다 — 뒤집으면 조용히 틀린다. */
        const val CENTER = "ST_MakePoint(?, ?)::geography"

        /**
         * 카드 한 장의 출처. 제목·주소만 번역에서 오고 나머지는 카탈로그가 확정한 표준값이다.
         *
         * 조인 조건의 `t.language = ?`는 [whereClause]가 담당한다 — 조회와 카운트가 같은 조건을
         * 쓰도록 조건을 한 곳에 모으기 위해서다. `UNIQUE(catalog_id, language)`가 있어 이 조인은
         * 카탈로그당 최대 1행이며, 번역이 없는 언어는 결과에서 빠진다.
         */
        const val CARD_SOURCE = "spot_catalog cat JOIN spot_catalog_translation t ON t.catalog_id = cat.id"

        /** 카드가 읽는 컬럼. [CARD_SOURCE]의 별칭과 짝이며 조회·카운트가 같은 이름을 본다. */
        const val CARD_COLUMNS =
            "cat.id AS id, t.title, t.address, cat.thumbnail_image_url, " +
                "cat.map_x, cat.map_y, cat.category, cat.copyright"
    }
}
