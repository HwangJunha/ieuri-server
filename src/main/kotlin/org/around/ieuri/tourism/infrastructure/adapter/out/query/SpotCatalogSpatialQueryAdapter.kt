package org.around.ieuri.tourism.infrastructure.adapter.out.query

import org.around.ieuri.tourism.application.port.out.query.CatalogOrigin
import org.around.ieuri.tourism.application.port.out.query.SpotCatalogSpatialQueryPort
import org.around.ieuri.tourism.domain.aggregate.CatalogStandardValues
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 분할 후보 탐지 전용 어댑터(SPEC-0029). 원본 간 최대 거리가 PostGIS
 * `ST_Distance(...::geography)`라 QueryDSL로 표현할 수 없어 이 어댑터에만 생 SQL이 남는다.
 *
 * **[SpotSpatialQueryAdapter]와 합치지 않는다.** 저쪽은 조회 요청이 부르는 반경 검색이고
 * 이쪽은 배치가 부르는 데이터 교정이다 — 읽는 테이블도(`spot` 대 `spot_catalog`) 수명도 다르다.
 */
@Component
class SpotCatalogSpatialQueryAdapter(
    private val jdbcTemplate: JdbcTemplate,
) : SpotCatalogSpatialQueryPort {

    /**
     * 원본이 [minOriginCount]개를 넘고 그 사이 최대 거리가 [minDistanceMeters] 이상인 카탈로그.
     *
     * 행 수로 먼저 좁히고 거리를 재는 순서가 중요하다 — 거리를 전 카탈로그에 대해 재면
     * 자기 조인이 원본 수의 제곱으로 커진다. 실측상 행 수 조건을 통과하는 카탈로그는 한 자릿수다.
     */
    @Transactional(readOnly = true)
    override fun findSplitCandidates(minOriginCount: Int, minDistanceMeters: Double): List<Long> = jdbcTemplate.query(
        SPLIT_CANDIDATE_SQL,
        { rs, _ -> rs.getLong("catalog_id") },
        minOriginCount,
        minDistanceMeters,
    )

    /**
     * 카탈로그에 매달린 원본 전부. 언어 순서로 읽는다 — 대표를 고르는 규칙이 그 순서이고,
     * 군집도 이 순서를 물려받아 결과가 실행마다 흔들리지 않는다.
     *
     * **좌표 범위 필터가 배정과 같다.** 배정이 한국 밖 좌표를 애초에 붙이지 않으므로 여기 걸릴
     * 행은 없지만, 상류가 좌표를 고쳐 범위를 벗어나면 `CatalogStandardValues`가 거부해 분할
     * 전체가 롤백된다 — 그 한 건이 나머지를 막지 않도록 같은 조건으로 거른다.
     */
    @Transactional(readOnly = true)
    override fun findOrigins(catalogId: Long): List<CatalogOrigin> = jdbcTemplate.query(
        ORIGIN_SQL,
        { rs, _ ->
            CatalogOrigin(
                spotId = rs.getLong("id"),
                language = DomainLocale.valueOf(rs.getString("language")),
                title = rs.getString("title"),
                address = rs.getString("address"),
                thumbnailImageUrl = rs.getString("thumbnail_image_url"),
                mapX = rs.getDouble("map_x"),
                mapY = rs.getDouble("map_y"),
                category = SpotCategory(rs.getString("category")),
                copyright = CopyrightType.valueOf(rs.getString("copyright")),
            )
        },
        catalogId,
        CatalogStandardValues.KOREA_LON_RANGE.start,
        CatalogStandardValues.KOREA_LON_RANGE.endInclusive,
        CatalogStandardValues.KOREA_LAT_RANGE.start,
        CatalogStandardValues.KOREA_LAT_RANGE.endInclusive,
    )

    private companion object {
        /**
         * `HAVING`으로 행 수를 먼저 거른 뒤 그 카탈로그에 한해 자기 조인으로 최대 거리를 잰다.
         *
         * `location`은 트리거가 map_x/map_y에서 파생시키는 geography 컬럼이라 엔티티에 매핑되어
         * 있지 않다 — HQL에서 보이지 않으므로 이 조회가 생 SQL인 두 번째 이유다.
         */
        val SPLIT_CANDIDATE_SQL = """
            WITH folded AS (
                SELECT catalog_id
                FROM tour.spot
                WHERE catalog_id IS NOT NULL
                GROUP BY catalog_id
                HAVING count(*) > ?
            )
            SELECT f.catalog_id
            FROM folded f
            JOIN tour.spot a ON a.catalog_id = f.catalog_id
            JOIN tour.spot b ON b.catalog_id = f.catalog_id AND b.id > a.id
            GROUP BY f.catalog_id
            HAVING max(ST_Distance(a.location, b.location)) >= ?
            ORDER BY f.catalog_id
        """.trimIndent()

        /** 언어 순서는 대표 선정 규칙과 같다(KO→EN→JA→ZH). 같은 언어가 여럿이면 id 순이다. */
        val ORIGIN_SQL = """
            SELECT id, language, title, address, thumbnail_image_url, map_x, map_y, category, copyright
            FROM tour.spot
            WHERE catalog_id = ?
              AND map_x BETWEEN ? AND ?
              AND map_y BETWEEN ? AND ?
            ORDER BY CASE language WHEN 'KO' THEN 0 WHEN 'EN' THEN 1 WHEN 'JA' THEN 2 ELSE 3 END, id
        """.trimIndent()
    }
}
