package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import org.around.ieuri.tourism.application.port.out.persistence.DistrictRepresentativeRefreshResult
import org.around.ieuri.tourism.application.port.out.persistence.DistrictRepresentativeUpdatePort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 시군구 대표 위치 선정 어댑터(SPEC-0015 · V46). PostGIS 함수와 대량 UPDATE라 생 SQL이다.
 * - 두 문장이 한 트랜잭션이어야 한다 — 사이에서 실패하면 문턱 미달 시군구에 낡은 대표 위치가 남는다.
 */
@Component
class DistrictRepresentativeJdbcAdapter(
    private val jdbcTemplate: JdbcTemplate,
) : DistrictRepresentativeUpdatePort {

    @Transactional
    override fun refreshRepresentativeLocations(
        cellSizeDegrees: Double,
        minSpotCount: Int,
    ): DistrictRepresentativeRefreshResult {
        // 순서: 먼저 채우고, 이번에 채워지지 않은 것을 지운다.
        // 두 문장 모두 인자가 (격자 크기, 최소 개수) 둘이다 — SQL의 `?` 개수와 맞아야 한다.
        val selected = jdbcTemplate.update(SELECT_SQL, cellSizeDegrees, minSpotCount)
        val cleared = jdbcTemplate.update(CLEAR_SQL, cellSizeDegrees, minSpotCount)
        return DistrictRepresentativeRefreshResult(selected = selected, cleared = cleared)
    }

    /** 테스트가 SQL의 `?` 개수를 확인하려고 접근한다. */
    internal companion object {
        /**
         * 시군구별로 spot이 가장 밀집한 격자를 찾아 그 격자 안의 실제 spot 좌표를 대표 위치로 넣는다.
         * - 격자 중심을 저장하면 시군구 경계 밖으로 나갈 수 있다 — 실제 spot으로 스냅한다.
         * - `ST_Intersects`라 경계선 위의 점을 포함한다(SPEC-0014).
         * - KO 행만으로 장소 수를 센다 — 언어별 행을 세면 번역이 많은 지역이 부풀는다.
         */
        val SELECT_SQL = """
            WITH gridded AS (
                SELECT sg.code,
                       ST_SnapToGrid(sp.location::geometry, ?) AS cell,
                       sp.location
                  FROM district sg
                  JOIN spot sp ON ST_Intersects(sg.boundary, sp.location)
                 WHERE sp.language = 'KO'
            ),
            cells AS (
                SELECT code,
                       cell,
                       count(*)                                    AS spot_count,
                       ST_Centroid(ST_Collect(location::geometry)) AS cell_center
                  FROM gridded
                 GROUP BY code, cell
                HAVING count(*) >= ?
            ),
            densest AS (
                -- 시군구마다 가장 밀집한 격자 하나. 동수면 cell 좌표로 결정적으로 고른다.
                SELECT DISTINCT ON (code) code, cell, spot_count, cell_center
                  FROM cells
                 ORDER BY code, spot_count DESC, cell
            ),
            snapped AS (
                -- 격자 중심에 가장 가까운 **실제 spot**으로 스냅한다. 중심 자체를 저장하지 않는다.
                SELECT d.code,
                       d.spot_count,
                       (SELECT g.location
                          FROM gridded g
                         WHERE g.code = d.code
                           AND g.cell = d.cell
                         ORDER BY g.location::geometry <-> d.cell_center
                         LIMIT 1) AS location
                  FROM densest d
            )
            UPDATE district s
            SET representative_location   = w.location,
                representative_spot_count = w.spot_count,
                representative_updated_at = now()
            FROM snapped w
            WHERE s.code = w.code
              AND w.location IS NOT NULL
        """.trimIndent()

        /**
         * 이번 실행에서 선정되지 못한 시군구의 낡은 값을 지운다. 선정 조건을 다시 세워 그 집합에 없는 행을 지운다.
         * - 이미 NULL인 행은 제외한다 — `cleared`는 이번에 값을 잃은 지역 수다.
         */
        val CLEAR_SQL = """
            UPDATE district
            SET representative_location   = NULL,
                representative_spot_count = NULL,
                representative_updated_at = now()
            WHERE representative_location IS NOT NULL
              AND code NOT IN (
                    -- 선정 조건과 **정확히 같은 기준**이어야 한다(KO 한정 · 같은 격자 · 같은 문턱).
                    -- 한쪽만 바뀌면 매 실행마다 넣었다 지웠다를 반복한다.
                    SELECT sg.code
                      FROM district sg
                      JOIN spot sp ON ST_Intersects(sg.boundary, sp.location)
                     WHERE sp.language = 'KO'
                     GROUP BY sg.code, ST_SnapToGrid(sp.location::geometry, ?)
                    HAVING count(*) >= ?
              )
        """.trimIndent()
    }
}
