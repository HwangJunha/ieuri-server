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
 * 분할 후보 탐지 어댑터(SPEC-0029). `ST_Distance(...::geography)` 때문에 생 SQL이다.
 * - 배치 전용 데이터 교정이다. 조회 요청의 [SpotSpatialQueryAdapter]와는 별개다.
 */
@Component
class SpotCatalogSpatialQueryAdapter(
    private val jdbcTemplate: JdbcTemplate,
) : SpotCatalogSpatialQueryPort {

    /** 원본이 [minOriginCount]개를 넘고 그 사이 최대 거리가 [minDistanceMeters] 이상인 카탈로그. */
    @Transactional(readOnly = true)
    override fun findSplitCandidates(minOriginCount: Int, minDistanceMeters: Double): List<Long> = jdbcTemplate.query(
        SPLIT_CANDIDATE_SQL,
        { rs, _ -> rs.getLong("catalog_id") },
        minOriginCount,
        minDistanceMeters,
    )

    /**
     * 카탈로그에 매달린 원본 전부. 언어 순서로 읽는다 — 대표 선정과 군집 결과가 실행마다 같다.
     * - 좌표 범위 필터가 배정과 같다 — 범위 밖 한 건이 분할 전체를 롤백시키지 않게 한다.
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
         * - 순서가 뒤집히면 자기 조인이 원본 수의 제곱으로 커진다.
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
