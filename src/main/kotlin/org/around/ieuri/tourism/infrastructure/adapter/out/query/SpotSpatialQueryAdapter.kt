package org.around.ieuri.tourism.infrastructure.adapter.out.query

import org.around.ieuri.tourism.application.port.out.query.NearbySpotSearch
import org.around.ieuri.tourism.application.port.out.query.SpotCandidate
import org.around.ieuri.tourism.application.port.out.query.SpotCard
import org.around.ieuri.tourism.application.port.out.query.SpotSearchResult
import org.around.ieuri.tourism.application.port.out.query.SpotSpatialQueryPort
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 반경 조회 전용 어댑터. PostGIS 함수(`ST_DWithin`·`ST_Distance`·`ST_MakePoint`)를 QueryDSL로
 * 표현할 수 없어 이 어댑터에만 생 SQL이 남는다.
 *
 * 조회와 카운트가 같은 조건을 써야 하므로 둘을 한 클래스에 두어 눈으로 대조할 수 있게 한다 —
 * 조건이 갈리면 hasNext와 totalCount가 어긋난다.
 *
 * **`spot`을 읽지 않는다**(SPEC-0019). 카탈로그와 번역만 본다 — `spot.language`로 거르면 그
 * 언어의 spot 행이 없는 장소가 목록에서 통째로 빠지는데, 이 스펙이 번역으로 채우는 대상이
 * 정확히 그 장소들이다.
 *
 * **거리 정렬은 번역 행 단독으로 한다**(SPEC-0038). 번역이 좌표·분류 사본을 갖고 있어
 * 카탈로그를 조인하지 않고도 걸러 낼 수 있다. 조인을 `LIMIT` 앞에 두면 정렬 대상을 확정하려고
 * 반경 안 전 행(가산 20km에서 8,925행)을 조인해야 한다 — `LIMIT`은 정렬 뒤에 적용되므로
 * 후보 상한을 낮춰도 이 횟수는 줄지 않는다. 썸네일·저작권만 카탈로그에 사본이 없어,
 * 살아남은 `LIMIT`건에만 PK로 조인한다.
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
        val (where, params) = whereClause(query)
        // 중심 좌표를 CTE로 한 번만 바인딩한다. SELECT·WHERE·ORDER BY 세 곳에서 쓰이는데
        // 그때마다 ST_MakePoint(?, ?)를 반복하면 위치 파라미터가 6개로 늘어 순서를 맞추기 어렵다.
        //
        // **정렬·LIMIT을 끝낸 뒤에 카탈로그를 조인한다.** ranked의 ORDER BY와 바깥의 ORDER BY는
        // 같은 키여야 한다 — 조인이 순서를 보존한다는 보장이 없다(POL-019·055).
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
                    // **클라이언트에 나가는 id는 카탈로그 id다**(SPEC-0019) — 목록의 단위가
                    // 언어별 spot 행이 아니라 장소이기 때문이다.
                    spotId = rs.getLong("id"),
                    title = rs.getString("title"),
                    address = rs.getString("address"),
                    thumbnailImageUrl = rs.getString("thumbnail_image_url"),
                    mapX = rs.getDouble("map_x"),
                    mapY = rs.getDouble("map_y"),
                    category = SpotCategory(rs.getString("category")),
                    // 컬럼에 enum 이름이 그대로 들어 있으므로 코드 변환(fromCode)이 아니라 valueOf다.
                    copyright = CopyrightType.valueOf(rs.getString("copyright")),
                    distanceMeters = rs.getDouble("distance_meters"),
                )
            },
            *(params + query.limit + query.offset).toTypedArray(),
        )
    }

    /**
     * 랭킹용 후보. **카탈로그를 조인하지 않는다** — 썸네일·저작권만 그쪽에 있는데,
     * 후보 전부에 조인하면 재배열 후 버려질 행까지 읽는다(SPEC-0038).
     *
     * cap 500·first 100 기준으로 조인 400회가 빠진다(건당 약 3버퍼 = 1,200버퍼).
     */
    @Transactional(readOnly = true)
    override fun searchNearbyCandidates(query: NearbySpotSearch): List<SpotCandidate> {
        val (where, params) = whereClause(query)
        val sql = """
            WITH c AS (SELECT $CENTER AS center)
            SELECT $RANKED_COLUMNS,
                   ST_Distance(t.location, c.center) AS distance_meters
            FROM spot_catalog_translation t, c
            WHERE $where
            ORDER BY ST_Distance(t.location, c.center), t.catalog_id
            LIMIT ? OFFSET ?
        """.trimIndent()

        return jdbcTemplate.query(
            sql,
            { rs, _ ->
                SpotCandidate(
                    spotId = rs.getLong("catalog_id"),
                    title = rs.getString("title"),
                    address = rs.getString("address"),
                    mapX = rs.getDouble("map_x"),
                    mapY = rs.getDouble("map_y"),
                    category = SpotCategory(rs.getString("category")),
                    distanceMeters = rs.getDouble("distance_meters"),
                )
            },
            *(params + query.limit + query.offset).toTypedArray(),
        )
    }

    /**
     * 살아남은 후보의 카드 필드. PK 조회라 id 개수만큼만 읽는다.
     *
     * **id 목록은 배열 한 개로 바인딩한다** — 플레이스홀더를 개수만큼 펼치면 SQL 본문이
     * 입력 크기에 비례해 자라고, 개수가 다를 때마다 다른 구문이 되어 계획 캐시가
     * 재사용되지 않는다.
     */
    @Transactional(readOnly = true)
    override fun findCards(spotIds: Collection<Long>): Map<Long, SpotCard> {
        if (spotIds.isEmpty()) return emptyMap()
        return jdbcTemplate.query(
            "SELECT id, thumbnail_image_url, copyright FROM spot_catalog WHERE id = ANY (?)",
            { rs, _ ->
                rs.getLong("id") to SpotCard(
                    thumbnailImageUrl = rs.getString("thumbnail_image_url"),
                    // 컬럼에 enum 이름이 그대로 들어 있으므로 코드 변환이 아니라 valueOf다.
                    copyright = CopyrightType.valueOf(rs.getString("copyright")),
                )
            },
            spotIds.toLongArray(),
        ).toMap()
    }

    /** [searchNearby]와 같은 조건에 걸리는 전체 건수. */
    @Transactional(readOnly = true)
    override fun countNearby(query: NearbySpotSearch): Int {
        val (where, params) = whereClause(query)
        // 카탈로그를 조인하지 않아도 수가 같다. 두 방향이 모두 필요하다 —
        // 늘지 않는 것은 `UNIQUE(catalog_id, language)`(카탈로그당 최대 1행)가 막고,
        // **줄지 않는 것은 `catalog_id`의 NOT NULL FK가 보장한다**(부모 없는 번역 행이 없다).
        // FK를 지우면 고아 행이 이 카운트에만 더해진다.
        return jdbcTemplate.queryForObject(
            "WITH c AS (SELECT $CENTER AS center) SELECT COUNT(*) FROM spot_catalog_translation t, c WHERE $where",
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
        // 파라미터 순서: CTE의 center(경도, 위도)가 먼저 오고, 그다음 조인·WHERE 조건 순서다.
        // **번역 기준으로 거른다**(SPEC-0019 R-9) — `t.language`는 spot 행의 언어가 아니라
        // 번역의 언어다. 번역이 채워진 카탈로그는 그 언어의 spot 행이 없어도 목록에 나온다.
        // 그것이 이 스펙 목적의 절반이다.
        //
        // **카테고리가 없는 카탈로그는 빠진다.** 카드가 카테고리를 필수로 요구하는데(도메인 enum에
        // 미분류 값이 없다) 임의의 기본값을 만들면 잘못된 분류로 노출된다.
        //
        // **좌표·분류는 번역 행의 사본을 본다**(SPEC-0038). 카탈로그가 확정한 값이 그대로
        // 복사돼 있고(실측 82,051행 불일치 0건) 기존 트리거가 `location`을 맞춘다. 번역의
        // `location`은 NOT NULL이라 좌표 결손으로 빠지는 행은 애초에 번역이 없는 행이다.
        val conditions = mutableListOf(
            "t.visible",
            "t.language = ?",
            "ST_DWithin(t.location, c.center, ?)",
        )
        val params = mutableListOf<Any>(
            query.center.longitude, query.center.latitude,
            query.language.name,
            query.radiusMeters.toDouble(),
        )

        // 다중 선택은 OR이며 IN으로 표현한다(SPEC-0020). 비어 있으면 조건 자체를 걸지 않는다.
        if (!query.categories.isUnfiltered) {
            val codes = query.categories.codes()
            conditions += "t.category IN (${codes.joinToString(",") { "?" }})"
            params.addAll(codes)
        }
        if (query.excludedSpotIds.isNotEmpty()) {
            // 이미 반응한 항목은 DB 단계에서 제외한다 — 조회 후 걸러내면 페이지가 size보다 작아진다.
            // **제외 대상은 카탈로그 id다** — 목록이 카탈로그 id를 내보내므로 호출자가 되돌려
            // 주는 것도 같은 축이다.
            //
            // **id 목록은 배열 한 개로 바인딩한다.** 플레이스홀더를 개수만큼 펼치면 SQL 본문이
            // 입력 크기에 비례해 자라고, 개수가 다를 때마다 다른 구문이 되어 계획 캐시가
            // 재사용되지 않는다 — 제외 집합은 회원의 반응 수만큼 커진다(실측 최대 179건).
            // `TasteSimilarityQueryJdbcAdapter`가 같은 이유로 같은 형태를 쓴다.
            conditions += "t.catalog_id <> ALL (?)"
            params += query.excludedSpotIds.toLongArray()
        }
        return conditions.joinToString(" AND ") to params
    }

    private companion object {
        /** 요청 좌표를 geography 점으로. ST_MakePoint는 (경도, 위도) 순서다 — 뒤집으면 조용히 틀린다. */
        const val CENTER = "ST_MakePoint(?, ?)::geography"

        /**
         * 정렬 단계가 번역 행에서 읽는 컬럼. 좌표·분류는 카탈로그 값의 사본이다.
         *
         * 여기에 카탈로그 컬럼을 더하면 조인이 `LIMIT` 앞으로 돌아가 이 최적화가 사라진다.
         */
        const val RANKED_COLUMNS = "t.catalog_id, t.title, t.address, t.map_x, t.map_y, t.category"

        /**
         * 카드가 읽는 컬럼. 썸네일·저작권만 카탈로그에서 오고 나머지는 정렬 단계가 이미 갖고 있다.
         *
         * **클라이언트에 나가는 id는 카탈로그 id다**(SPEC-0019) — 목록의 단위가 언어별 spot 행이
         * 아니라 장소이기 때문이다.
         */
        const val CARD_COLUMNS =
            "r.catalog_id AS id, r.title, r.address, cat.thumbnail_image_url, " +
                "r.map_x, r.map_y, r.category, cat.copyright, r.distance_meters"
    }
}
