package org.around.ieuri.tourism.infrastructure.adapter.out.query

import org.around.ieuri.tourism.application.port.out.query.NearbyKeysetSearch
import org.around.ieuri.tourism.application.port.out.query.NearbySpotSearch
import org.around.ieuri.tourism.application.port.out.query.SpotSearchResult
import org.around.ieuri.tourism.application.port.out.query.SpotSpatialQueryPort
import org.around.ieuri.tourism.domain.vo.CategorySelection
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet

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
) : SpotSpatialQueryPort {

    /**
     * 중심에서 반경 안의 관광지 한 페이지. 거리 오름차순, 등거리는 id 오름차순이다 —
     * `ORDER BY`의 `id`를 빼면 등거리 행의 순서가 요청마다 달라져 항목이 중복·누락된다.
     */
    @Transactional(readOnly = true)
    override fun searchNearby(query: NearbySpotSearch): List<SpotSearchResult> {
        val (where, params) = whereClause(query.language, query.center, query.radiusMeters, query.categories, query.excludedSpotIds)
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
            { rs, _ -> rs.toSearchResult() },
            *(params + query.limit + query.offset).toTypedArray(),
        )
    }

    /** 카드 한 장. 두 조회가 같은 컬럼([CARD_COLUMNS])을 읽으므로 한 곳에서 옮긴다. */
    private fun ResultSet.toSearchResult(): SpotSearchResult = SpotSearchResult(
        // **클라이언트에 나가는 id는 카탈로그 id다**(SPEC-0019) — 목록의 단위가
        // 언어별 spot 행이 아니라 장소이기 때문이다.
        spotId = getLong("id"),
        title = getString("title"),
        address = getString("address"),
        // 카탈로그의 썸네일은 nullable이다. 카드 자리를 비워 두는 편이 조회를 통째로
        // 실패시키는 것보다 낫다.
        thumbnailImageUrl = getString("thumbnail_image_url"),
        mapX = getDouble("map_x"),
        mapY = getDouble("map_y"),
        category = SpotCategory(getString("category")),
        // 컬럼에 enum 이름이 그대로 들어 있으므로 코드 변환(fromCode)이 아니라 valueOf다.
        copyright = CopyrightType.valueOf(getString("copyright")),
        distanceMeters = getDouble("distance_meters"),
    )

    /**
     * [searchNearby]와 같은 후보·같은 정렬을 (거리, id) 키셋으로(SPEC-0035 R3). 거리가 계산식이라
     * WHERE에서 별칭을 못 써 서브쿼리로 한 겹 감싼다(`FestivalQueryJdbcAdapter`와 같은 모양). `limit + 1`건을
     * 읽어 호출자가 다음 페이지를 판정한다. 반경 필터(`ST_DWithin`)는 안쪽에 남아 GiST 인덱스를 타고,
     * 바깥의 seek 조건은 플래너가 서브쿼리 안으로 밀어 넣는다 — 거리는 계산값이라 어느 위치든 인덱스가 없다.
     */
    @Transactional(readOnly = true)
    override fun searchNearbyAfter(query: NearbyKeysetSearch): List<SpotSearchResult> {
        val (where, whereParams) = whereClause(
            query.language, query.center, query.radiusMeters, query.categories, query.excludedSpotIds,
        )
        val params = whereParams.toMutableList()
        val seek = if (query.after != null) {
            params += query.after.distanceMeters
            params += query.after.spotId
            "WHERE (distance_meters, id) > (?, ?)"
        } else {
            ""
        }
        params += query.limit + 1

        val sql = """
            WITH c AS (SELECT $CENTER AS center)
            SELECT * FROM (
                SELECT $CARD_COLUMNS,
                       ST_Distance(cat.location, c.center) AS distance_meters
                FROM $CARD_SOURCE, c
                WHERE $where
            ) ranked
            $seek
            ORDER BY distance_meters ASC, id ASC
            LIMIT ?
        """.trimIndent()

        return jdbcTemplate.query(sql, { rs, _ -> rs.toSearchResult() }, *params.toTypedArray())
    }

    /** [searchNearby]와 같은 조건에 걸리는 전체 건수. */
    @Transactional(readOnly = true)
    override fun countNearby(query: NearbySpotSearch): Int {
        val (where, params) = whereClause(query.language, query.center, query.radiusMeters, query.categories, query.excludedSpotIds)
        return jdbcTemplate.queryForObject(
            "WITH c AS (SELECT $CENTER AS center) SELECT COUNT(*) FROM $CARD_SOURCE, c WHERE $where",
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
    private fun whereClause(
        language: DomainLocale,
        center: Coordinates,
        radiusMeters: Int,
        categories: CategorySelection,
        excludedSpotIds: Set<Long>,
    ): Pair<String, List<Any>> {
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
            "ST_DWithin(cat.location, c.center, ?)",
        )
        val params = mutableListOf<Any>(
            center.longitude, center.latitude,
            language.name,
            radiusMeters.toDouble(),
        )

        // 다중 선택은 OR이며 IN으로 표현한다(SPEC-0020). 비어 있으면 조건 자체를 걸지 않는다.
        if (!categories.isUnfiltered) {
            val codes = categories.codes()
            conditions += "cat.category IN (${codes.joinToString(",") { "?" }})"
            params.addAll(codes)
        }
        if (excludedSpotIds.isNotEmpty()) {
            // 이미 반응한 항목은 DB 단계에서 제외한다 — 조회 후 걸러내면 페이지가 size보다 작아진다.
            // **제외 대상은 카탈로그 id다** — 목록이 카탈로그 id를 내보내므로 호출자가 되돌려
            // 주는 것도 같은 축이다.
            conditions += "cat.id NOT IN (${excludedSpotIds.joinToString(",") { "?" }})"
            params.addAll(excludedSpotIds)
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
        const val CARD_SOURCE = "spot_catalog cat JOIN spot_catalog_translation t ON t.catalog_id = cat.id AND t.visible"

        /** 카드가 읽는 컬럼. [CARD_SOURCE]의 별칭과 짝이며 조회·카운트가 같은 이름을 본다. */
        const val CARD_COLUMNS =
            "cat.id AS id, t.title, t.address, cat.thumbnail_image_url, " +
                "cat.map_x, cat.map_y, cat.category, cat.copyright"
    }
}
