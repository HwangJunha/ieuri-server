package org.around.ieuri.tourism.infrastructure.adapter.out.query

import org.around.ieuri.tourism.application.port.out.query.MapCellQueryPort
import org.around.ieuri.tourism.application.query.CellSpotView
import org.around.ieuri.tourism.domain.vo.AreaName
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.MapCellDistrictRow
import org.around.ieuri.tourism.domain.vo.ReactionType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet

/**
 * 전국 지도 칸 조회 어댑터(SPEC-0030). `district`의 geography 컬럼이 JPA에 매핑되지 않아 생 SQL이다.
 * - 컬럼을 `::geometry`로 캐스팅하지 않는다 — GiST 인덱스가 죽는다. SELECT 목록의 `ST_X`/`ST_Y`만 예외다.
 */
@Component
class MapCellQueryJdbcAdapter(
    private val jdbcTemplate: JdbcTemplate,
) : MapCellQueryPort {

    @Transactional(readOnly = true)
    override fun findAllCellDistricts(): List<MapCellDistrictRow> = jdbcTemplate.query("$CELL_DISTRICT_SELECT ORDER BY mc.id, d.code") { rs, _ ->
        rs.toCellDistrictRow()
    }

    @Transactional(readOnly = true)
    override fun findCellDistricts(cellId: Long): List<MapCellDistrictRow> = jdbcTemplate.query(
        "$CELL_DISTRICT_SELECT WHERE mc.id = ? ORDER BY d.code",
        { rs, _ -> rs.toCellDistrictRow() },
        cellId,
    )

    @Transactional(readOnly = true)
    override fun findCellDistrictsIn(cellIds: Collection<Long>): List<MapCellDistrictRow> {
        if (cellIds.isEmpty()) return emptyList()

        return jdbcTemplate.query(
            "$CELL_DISTRICT_SELECT WHERE mc.id = ANY(?::bigint[]) ORDER BY mc.id, d.code",
            { rs, _ -> rs.toCellDistrictRow() },
            cellIds.toTypedArray(),
        )
    }

    /**
     * 칸에 속한 장소(R11). 좋아요 내림차순, 같은 수는 id 오름차순.
     * - 활성 언어에 번역이 없는 장소는 빠지고, 좋아요 0건인 장소는 0으로 남는다.
     * - 그래서 반응은 `LEFT JOIN`이고 조건이 `ON`에 있으며 `COUNT(r.id)`다 — 하나라도 바꾸면 0건 장소가 사라지거나 1이 된다.
     */
    @Transactional(readOnly = true)
    override fun findCellSpots(
        cellId: Long,
        language: DomainLocale,
        limit: Int,
    ): List<CellSpotView> = jdbcTemplate.query(
        """
            SELECT cat.id, t.title, cat.thumbnail_image_url, cat.category, cat.map_x, cat.map_y,
                   COUNT(r.id) AS like_count
            FROM tour.spot_catalog cat
            JOIN tour.spot_catalog_translation t
              ON t.catalog_id = cat.id AND t.language = ? AND t.visible
            LEFT JOIN tour.spot_reaction r
              ON r.catalog_id = cat.id AND r.reaction = ? AND r.language = ?
            JOIN tour.map_cell_district mcd ON mcd.district_code = cat.district_code
            WHERE mcd.cell_id = ?
            GROUP BY cat.id, t.title, cat.thumbnail_image_url, cat.category, cat.map_x, cat.map_y
            ORDER BY like_count DESC, cat.id ASC
            LIMIT ?
        """.trimIndent(),
        { rs, _ ->
            CellSpotView(
                id = rs.getLong("id"),
                title = rs.getString("title"),
                thumbnailImageUrl = rs.getString("thumbnail_image_url"),
                categoryCode = rs.getString("category"),
                coordinates = Coordinates(
                    latitude = rs.getDouble("map_y"),
                    longitude = rs.getDouble("map_x"),
                ),
                likeCount = rs.getLong("like_count"),
            )
        },
        language.name,
        ReactionType.LIKE.name,
        language.name,
        cellId,
        limit,
    )

    private fun ResultSet.toCellDistrictRow(): MapCellDistrictRow = MapCellDistrictRow(
        cellId = getLong("cell_id"),
        districtCode = getString("code"),
        cellName = AreaName(
            ko = getString("cell_name_ko"),
            en = getString("cell_name_en"),
            ja = getString("cell_name_ja"),
            zh = getString("cell_name_zh"),
        ),
        provinceName = AreaName(
            ko = getString("area_name_ko"),
            en = getString("area_name_en"),
            ja = getString("area_name_ja"),
            zh = getString("area_name_zh"),
        ),
        provinceCode = getString("area_cd"),
        center = Coordinates(latitude = getDouble("center_lat"), longitude = getDouble("center_lng")),
    )

    private companion object {
        /**
         * 칸 ↔ 시군구 행. 대표 좌표는 `center`다(`representative_location`은 일부 시군구에서 null).
         * - 시도는 `district`에서 온다(한 칸의 시군구는 같은 시도).
         * - 칸의 최종 순서는 도메인(`MapCellGrouping.fold`)이 정한다. `ORDER BY`는 한 칸의 시군구를 붙여 놓기 위함이다.
         */
        const val CELL_DISTRICT_SELECT = """
            SELECT d.code, mcd.cell_id, d.area_cd,
                   mc.name_ko AS cell_name_ko, mc.name_en AS cell_name_en,
                   mc.name_ja AS cell_name_ja, mc.name_zh AS cell_name_zh,
                   a.name_ko AS area_name_ko, a.name_en AS area_name_en,
                   a.name_ja AS area_name_ja, a.name_zh AS area_name_zh,
                   ST_Y(d.center::geometry) AS center_lat,
                   ST_X(d.center::geometry) AS center_lng
            FROM tour.map_cell mc
            JOIN tour.map_cell_district mcd ON mcd.cell_id = mc.id
            JOIN tour.district d ON d.code = mcd.district_code
            JOIN tour.area a ON a.area_cd = d.area_cd
        """
    }
}
