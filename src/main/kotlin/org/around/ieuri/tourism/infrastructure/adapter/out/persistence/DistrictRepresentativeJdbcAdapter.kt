package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import org.around.ieuri.tourism.application.port.out.persistence.DistrictRepresentativeRefreshResult
import org.around.ieuri.tourism.application.port.out.persistence.DistrictRepresentativeUpdatePort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 시군구 대표 위치 선정 어댑터(SPEC-0015 · V46). PostGIS 함수와 대량 UPDATE라 생 SQL이며,
 * `boundary` · `representative_location`은 JPA로 표현할 수 없어 엔티티에 매핑되어 있지도 않다.
 *
 * **두 문장이 한 트랜잭션이어야 한다** — 선정과 정리 사이에서 실패하면 문턱에 미달한 시군구에
 * 낡은 대표 위치가 남는다.
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
        // 순서가 중요하다 — 먼저 채우고 나서, 이번에 채워지지 않은 것을 지운다.
        // 정리 문장이 선정 조건을 다시 계산하는 이유는 CLEAR_SQL 주석 참조.
        //
        // 두 문장 모두 인자가 (격자 크기, 최소 개수) 둘뿐이다 — SQL의 `?` 개수와 반드시
        // 맞아야 하며, 어긋나면 실행 시점에야 "column index is out of range"로 드러난다.
        val selected = jdbcTemplate.update(SELECT_SQL, cellSizeDegrees, minSpotCount)
        val cleared = jdbcTemplate.update(CLEAR_SQL, cellSizeDegrees, minSpotCount)
        return DistrictRepresentativeRefreshResult(selected = selected, cleared = cleared)
    }

    /** SQL의 `?` 개수와 인자 개수가 어긋나면 실행 시점에야 드러나므로 테스트가 여기 접근한다. */
    internal companion object {
        /**
         * 시군구별로 spot이 가장 밀집한 격자를 찾아 **그 격자 안의 실제 spot 좌표**를 대표 위치로 넣는다.
         *
         * **격자 중심(ST_Centroid)을 그대로 저장하면 안 된다** — 실측에서 252개 중 2개가 자기
         * 시군구 경계 밖으로 나갔다(오목·해안 지형). 실제 spot으로 스냅하면 0개이며, 그 지점에
         * spot이 존재한다는 것도 함께 보장된다.
         *
         * `ST_Within`이 아니라 `ST_Intersects`다 — 경계선 위의 점을 배제하지 않는다(SPEC-0014).
         *
         * **`language = 'KO'`로 거르는 이유**: spot은 언어마다 다른 행이라(같은 장소가 최대 4행)
         * 거르지 않으면 번역이 잘 된 지역일수록 밀집도가 부풀어 문턱을 쉽게 넘는다. 대표 위치는
         * "번역이 많은 곳"이 아니라 "갈 곳이 많은 곳"이어야 하므로 KO 행만으로 장소 수를 센다.
         * KO는 전 지역에 존재한다(실측: 252개 시군구 전부).
         *
         * **`active = TRUE`도 함께 건다**(SPEC-0018). 약국·편의점이 밀집도를 부풀리면 "갈 곳이
         * 많은 곳"이 아니라 "상점이 많은 곳"이 뽑히고, 스냅 대상이 조회되지 않는 spot이 될 수도
         * 있다. [CLEAR_SQL]에도 같은 조건이 있어야 한다.
         */
        val SELECT_SQL = """
            WITH gridded AS (
                SELECT sg.code,
                       ST_SnapToGrid(sp.location::geometry, ?) AS cell,
                       sp.location
                  FROM district sg
                  JOIN spot sp ON ST_Intersects(sg.boundary, sp.location)
                 WHERE sp.language = 'KO'
                   AND sp.active = TRUE
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
         * 이번 실행에서 선정되지 못한 시군구의 낡은 값을 지운다. **spot이 줄어 문턱 아래로
         * 내려간 지역이 예전 대표 위치를 계속 쓰는 것을 막는다.**
         *
         * 갱신 시각으로 판정하지 않는다 — `now()`는 트랜잭션 내내 같은 값이라 "방금 갱신됨"과
         * "예전에 갱신됨"을 시각만으로 가를 수 없다. 대신 선정 조건을 그대로 다시 세워
         * **문턱을 넘긴 시군구 집합에 없는 행**을 지운다.
         *
         * 이미 NULL인 행은 제외해 `cleared`가 "이번에 값을 잃은 지역 수"만 세도록 한다.
         */
        val CLEAR_SQL = """
            UPDATE district
            SET representative_location   = NULL,
                representative_spot_count = NULL,
                representative_updated_at = now()
            WHERE representative_location IS NOT NULL
              AND code NOT IN (
                    -- 선정 조건과 **정확히 같은 기준**이어야 한다(KO·활성 한정 · 같은 격자 · 같은 문턱).
                    -- 한쪽만 바뀌면 매 실행마다 넣었다 지웠다를 반복한다.
                    SELECT sg.code
                      FROM district sg
                      JOIN spot sp ON ST_Intersects(sg.boundary, sp.location)
                     WHERE sp.language = 'KO'
                   AND sp.active = TRUE
                     GROUP BY sg.code, ST_SnapToGrid(sp.location::geometry, ?)
                    HAVING count(*) >= ?
              )
        """.trimIndent()
    }
}
