package org.around.ieuri.tourism.infrastructure.adapter.out.query

import org.around.ieuri.tourism.application.port.out.query.MapCellQueryPort
import org.around.ieuri.tourism.application.query.CellSpotView
import org.around.ieuri.tourism.domain.vo.AreaName
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.LikeCountCursor
import org.around.ieuri.tourism.domain.vo.MapCellDistrictRow
import org.around.ieuri.tourism.domain.vo.ReactionType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet

/**
 * 전국 지도 칸 조회 어댑터(SPEC-0030).
 *
 * `district.boundary`·`center`가 `geography`라 JPA에 매핑되어 있지 않고, 그래서 이 테이블을
 * 읽는 질의는 생 SQL로 남는다([RegionMetricQueryAdapter]와 같은 선례).
 *
 * **컬럼을 캐스팅하지 않는다.** `boundary`가 geography GiST 인덱스라 리터럴 쪽을 geography로
 * 맞춰야 인덱스를 탄다 — 반대로 컬럼을 `::geometry`로 바꾸면 인덱스가 죽는다(실측 선례).
 * `ST_X`/`ST_Y`는 geography를 받지 않아 `::geometry`가 불가피하지만 SELECT 목록이라 무해하다.
 */
@Component
class MapCellQueryJdbcAdapter(
    private val jdbcTemplate: JdbcTemplate,
) : MapCellQueryPort {

    @Transactional(readOnly = true)
    override fun findAllCellDistricts(): List<MapCellDistrictRow> =
        jdbcTemplate.query("$CELL_DISTRICT_SELECT ORDER BY mc.id, d.code") { rs, _ ->
            rs.toCellDistrictRow()
        }

    @Transactional(readOnly = true)
    override fun findCellDistricts(cellId: Long): List<MapCellDistrictRow> =
        jdbcTemplate.query(
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
     * 칸에 속한 장소(R11). 좋아요 내림차순 + id 오름차순.
     *
     * 정렬 두 축의 방향이 달라 `like_count`에 -1을 곱해 부호를 맞추면 seek이 행 값 비교 한
     * 줄이 된다(`popularSpots`의 선례). 부호 반전은 여기서만 하며 커서에는 원래 값이 실린다.
     *
     * 집계값이라 별칭을 WHERE에서 참조할 수 없어 서브쿼리로 한 겹 감싼다. 좋아요가 0건인
     * 장소도 나와야 하므로 `LEFT JOIN`이다 — INNER면 아무도 누르지 않은 장소가 통째로 빠진다.
     */
    @Transactional(readOnly = true)
    override fun findCellSpots(
        cellId: Long,
        language: DomainLocale,
        limit: Int,
        after: LikeCountCursor?,
    ): List<CellSpotView> {
        val params = mutableListOf<Any>(language.name, ReactionType.LIKE.name, language.name, cellId)

        val seek = if (after != null) {
            params += -after.likeCount
            params += after.spotId
            "WHERE (-like_count, id) > (?, ?)"
        } else {
            ""
        }
        params += limit

        return jdbcTemplate.query(
            """
            SELECT * FROM (
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
            ) ranked
            $seek
            ORDER BY -like_count, id
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
            *params.toTypedArray(),
        )
    }

    /**
     * 여러 칸의 첫 페이지를 한 질의로 읽는다(SPEC-0032 R24).
     *
     * 칸마다 상위 [limit]건을 골라야 하므로 `ROW_NUMBER() OVER (PARTITION BY cell_id ...)`를
     * 쓴다. 커서가 없어 파티션마다 조건이 갈리지 않으므로 **SQL이 칸 수를 따라 커지지 않는다** —
     * 칸 id는 배열 하나로 넘어간다.
     *
     * 정렬 축과 부호 반전 규약은 [findCellSpots]와 같다.
     */
    @Transactional(readOnly = true)
    override fun findFirstCellSpotsIn(
        cellIds: Collection<Long>,
        language: DomainLocale,
        limit: Int,
    ): Map<Long, List<CellSpotView>> {
        if (cellIds.isEmpty()) return emptyMap()

        val rows = jdbcTemplate.query(
            """
            SELECT * FROM (
                SELECT ranked.*,
                       ROW_NUMBER() OVER (PARTITION BY cell_id ORDER BY -like_count, id) AS rn
                FROM (
                    SELECT mcd.cell_id, cat.id, t.title, cat.thumbnail_image_url, cat.category,
                           cat.map_x, cat.map_y, COUNT(r.id) AS like_count
                    FROM tour.spot_catalog cat
                    JOIN tour.spot_catalog_translation t
                      ON t.catalog_id = cat.id AND t.language = ? AND t.visible
                    LEFT JOIN tour.spot_reaction r
                      ON r.catalog_id = cat.id AND r.reaction = ? AND r.language = ?
                    JOIN tour.map_cell_district mcd ON mcd.district_code = cat.district_code
                    WHERE mcd.cell_id = ANY(?::bigint[])
                    GROUP BY mcd.cell_id, cat.id, t.title, cat.thumbnail_image_url, cat.category,
                             cat.map_x, cat.map_y
                ) ranked
            ) numbered
            WHERE rn <= ?
            ORDER BY cell_id, rn
            """.trimIndent(),
            { rs, _ ->
                rs.getLong("cell_id") to CellSpotView(
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
            // 칸 id를 배열 하나로 넘긴다 — IN 목록으로 펼치면 SQL이 입력 수를 따라 커진다.
            cellIds.toTypedArray(),
            limit,
        )

        // 장소가 없는 칸은 키가 없다 — 채우는 것은 호출자(DataLoader)의 몫이다.
        return rows.groupBy({ it.first }, { it.second })
    }

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
         * 칸 대표 좌표는 `center`를 쓴다 — `representative_location`은 26개 시군구에서 null이라
         * 그 칸이 지도에서 사라진다. 추천(관광지 밀집 지점)과 지도(칸을 가리키는 점)는 다른 요구다.
         *
         * **시도는 `district`에서 온다.** 한 칸의 시군구가 전부 같은 시도라(실측) 어느 행을
         * 골라도 같으며, 칸이 시도를 따로 들고 있을 이유가 없다.
         *
         * **정렬은 여기서 하지 않는다.** 칸의 최종 순서는 도메인(`MapCellGrouping.fold`)이
         * 정한다 — `groupBy`가 입력 순서를 보존하지 않으므로 SQL에서 정렬해도 무의미하다.
         * `ORDER BY`가 남아 있는 것은 한 칸의 시군구가 붙어 나오게 하기 위함이다.
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
