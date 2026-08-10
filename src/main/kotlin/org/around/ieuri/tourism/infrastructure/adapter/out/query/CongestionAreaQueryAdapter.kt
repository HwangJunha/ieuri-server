package org.around.ieuri.tourism.infrastructure.adapter.out.query

import org.around.ieuri.tourism.application.port.out.query.CongestionAreaQueryPort
import org.around.ieuri.tourism.application.query.CongestionAreaBoundaryView
import org.around.ieuri.tourism.domain.vo.AreaName
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.MapBounds
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet

/**
 * 서울 구역 메타데이터 조회 어댑터(SPEC-0014 R6·R16·R17).
 *
 * `congestion_area.boundary`가 `geography(Polygon,4326)`이라 **JPA 엔티티에 매핑되어 있지
 * 않다** — 매핑하면 `ddl-auto: validate`가 기동 시 실패한다. 그래서 이 테이블을 읽는 질의는
 * 전부 여기 생 SQL로 남는다(`SpotSpatialQueryAdapter`와 같은 선례).
 */
@Component
class CongestionAreaQueryAdapter(
    private val jdbcTemplate: JdbcTemplate,
) : CongestionAreaQueryPort {

    /**
     * 121곳 전체의 경계와 메타데이터(R17).
     *
     * 폴리곤은 `ST_AsText`가 아니라 좌표 배열로 뽑는다 — WKT를 문자열 파싱하면 구분자
     * 처리에 실수가 끼기 쉽고, `ST_DumpPoints`는 점마다 행이 생겨 12,226행이 된다.
     * `ST_AsGeoJSON`이 좌표 순서(`[lng, lat]`)와 닫힘을 그대로 보존한다.
     */
    @Transactional(readOnly = true)
    override fun findAllBoundaries(locale: DomainLocale): List<CongestionAreaBoundaryView> =
        jdbcTemplate.query(
            """
            SELECT area_cd, category, name_ko, name_en, name_ja, name_zh,
                   ST_AsGeoJSON(boundary) AS boundary_json
            FROM congestion_area
            ORDER BY area_cd
            """.trimIndent(),
        ) { rs, _ ->
            CongestionAreaBoundaryView(
                id = rs.getString("area_cd"),
                name = rs.areaName().resolve(locale),
                category = rs.getString("category"),
                polygon = parseOuterRing(rs.getString("boundary_json")),
            )
        }

    /**
     * bbox에 걸치는 구역 코드(R6). 폴리곤이 뷰포트와 겹치면 포함이다.
     *
     * `&&`(바운딩박스 겹침)로 인덱스를 먼저 태우고 `ST_Intersects`로 정확히 판정한다 —
     * `&&`만 쓰면 폴리곤의 사각 외접이 겹칠 뿐 실제로는 벗어난 구역이 섞인다.
     */
    @Transactional(readOnly = true)
    override fun findAreaCodesWithin(bounds: MapBounds): List<String> =
        jdbcTemplate.query(
            """
            SELECT area_cd FROM congestion_area
            WHERE boundary && ST_MakeEnvelope(?, ?, ?, ?, 4326)::geography
              AND ST_Intersects(boundary, ST_MakeEnvelope(?, ?, ?, ?, 4326)::geography)
            ORDER BY area_cd
            """.trimIndent(),
            { rs, _ -> rs.getString("area_cd") },
            // envelope은 (minLng, minLat, maxLng, maxLat) 순서다 — 두 조건에 각각 넘긴다.
            bounds.minLng, bounds.minLat, bounds.maxLng, bounds.maxLat,
            bounds.minLng, bounds.minLat, bounds.maxLng, bounds.maxLat,
        )

    /** 실재하는 구역의 이름만. 없는 코드는 빠지므로 호출자가 부재를 판정한다(R6). */
    @Transactional(readOnly = true)
    override fun findNamesByCodes(areaCodes: List<String>): Map<String, AreaName> {
        if (areaCodes.isEmpty()) return emptyMap()

        val placeholders = areaCodes.joinToString(",") { "?" }
        return jdbcTemplate.query(
            """
            SELECT area_cd, name_ko, name_en, name_ja, name_zh
            FROM congestion_area
            WHERE area_cd IN ($placeholders)
            """.trimIndent(),
            { rs, _ -> rs.getString("area_cd") to rs.areaName() },
            *areaCodes.toTypedArray(),
        ).toMap()
    }

    private fun ResultSet.areaName() = AreaName(
        ko = getString("name_ko"),
        en = getString("name_en"),
        ja = getString("name_ja"),
        zh = getString("name_zh"),
    )

    /**
     * GeoJSON Polygon → 외곽 링 `[[lng, lat], ...]`.
     *
     * **첫 번째 링만 취한다.** 원본에서 홀이 있는 곳은 POI096 하나뿐이고 적재 시 외곽만
     * 넣었으므로 실제로 링이 둘인 행은 없지만, 나중에 홀이 있는 구역이 적재돼도 계약
     * (단일 폐곡선)이 깨지지 않게 여기서도 방어한다.
     */
    private fun parseOuterRing(geoJson: String): List<List<Double>> {
        val coordinates = MAPPER.readTree(geoJson).get("coordinates") ?: return emptyList()
        val outerRing = coordinates.firstOrNull() ?: return emptyList()
        return buildList {
            for (point in outerRing) {
                add(listOf(point.get(0).asDouble(), point.get(1).asDouble()))
            }
        }
    }

    private companion object {
        val MAPPER = tools.jackson.databind.json.JsonMapper.builder().build()
    }
}
