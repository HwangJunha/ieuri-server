package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import org.around.ieuri.tourism.application.port.out.persistence.DistrictRepresentativeRefreshResult
import org.around.ieuri.tourism.application.port.out.persistence.DistrictRepresentativeUpdatePort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 시군구 대표 위치 선정 · 카테고리 집계 어댑터(SPEC-0015 · V46 · V50). PostGIS 함수와 대량
 * UPDATE라 생 SQL이며, `boundary` · `representative_location`은 JPA로 표현할 수 없어 엔티티에
 * 매핑되어 있지도 않다.
 *
 * **대표 위치의 두 문장이 한 트랜잭션이어야 한다** — 선정과 정리 사이에서 실패하면 문턱에
 * 미달한 시군구에 낡은 대표 위치가 남는다.
 *
 * 카테고리 집계가 같은 어댑터에 있는 이유는 같은 공간 조인을 쓰고 같은 배치에서 갱신되어야
 * 하기 때문이다(포트 주석 참조).
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

    /** 한 문장이라 [refreshRepresentativeLocations]와 트랜잭션을 공유할 필요가 없다. */
    @Transactional
    override fun refreshCategoryMix(): Int = jdbcTemplate.update(CATEGORY_MIX_SQL)

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

        /**
         * 시군구별 카테고리 구성비 집계(SPEC-0015 · V50). 대표 위치와 같은 공간 조인이다.
         *
         * **LEFT JOIN이어야 한다.** 안쪽 조인으로 쓰면 spot이 하나도 없는 시군구가 UPDATE 대상에서
         * 빠져 예전 집계가 무기한 남는다. LEFT JOIN이면 그런 시군구도 갱신되며, `counted.code`가
         * NULL이라 아래 CASE가 전 컬럼을 **NULL로 되돌린다 — 0이 아니다**(POL-043: 없는 데이터와
         * 0건은 다르다. 0으로 채우면 "관광지가 하나도 없는 곳"과 구분되지 않는다).
         *
         * **`language = 'KO'` 한정이다** — spot은 언어마다 다른 행이라(같은 장소가 최대 4행) 전
         * 언어를 세면 번역이 잘 된 지역의 구성비가 부풀려진다(SELECT_SQL과 같은 이유).
         *
         * `ST_Within`이 아니라 `ST_Intersects`다 — 경계선 위의 점을 배제하지 않는다.
         *
         * 카테고리 목록은 SpotCategory enum과 맞물려 있다. **enum에 값을 추가하면 여기도 컬럼과
         * 함께 늘려야 한다** — 누락되면 그 카테고리는 total에만 잡히고 어디에도 분류되지 않는다.
         * TRANSPORT는 외국어 전용이라 KO 집계에 나타나지 않으므로 컬럼이 없다.
         */
        val CATEGORY_MIX_SQL = """
            WITH counted AS (
                SELECT sg.code,
                       count(*)                                                       AS total,
                       count(*) FILTER (WHERE sp.category = 'TOURIST_SPOT')           AS tourist_spot,
                       count(*) FILTER (WHERE sp.category = 'RESTAURANT')             AS restaurant,
                       count(*) FILTER (WHERE sp.category = 'LEISURE_SPORTS')         AS leisure_sports,
                       count(*) FILTER (WHERE sp.category = 'SHOPPING')               AS shopping,
                       count(*) FILTER (WHERE sp.category = 'CULTURAL_FACILITY')      AS cultural_facility,
                       count(*) FILTER (WHERE sp.category = 'FESTIVAL_EVENT')         AS festival_event,
                       count(*) FILTER (WHERE sp.category = 'LODGING')                AS lodging,
                       count(*) FILTER (WHERE sp.category = 'TRAVEL_COURSE')          AS travel_course
                  FROM district sg
                  JOIN spot sp ON ST_Intersects(sg.boundary, sp.location)
                 WHERE sp.language = 'KO'
                 GROUP BY sg.code
            )
            UPDATE district d
            SET spot_count_total             = c.total,
                spot_count_tourist_spot      = c.tourist_spot,
                spot_count_restaurant        = c.restaurant,
                spot_count_leisure_sports    = c.leisure_sports,
                spot_count_shopping          = c.shopping,
                spot_count_cultural_facility = c.cultural_facility,
                spot_count_festival_event    = c.festival_event,
                spot_count_lodging           = c.lodging,
                spot_count_travel_course     = c.travel_course,
                spot_count_updated_at        = now()
            FROM (
                -- 전 시군구를 대상으로 두고 집계를 붙인다. 집계가 없는 시군구는 전 값이 NULL이 되어
                -- 위 SET이 예전 집계를 지운다.
                SELECT d2.code            AS code,
                       c2.total           AS total,
                       c2.tourist_spot      AS tourist_spot,
                       c2.restaurant        AS restaurant,
                       c2.leisure_sports    AS leisure_sports,
                       c2.shopping          AS shopping,
                       c2.cultural_facility AS cultural_facility,
                       c2.festival_event    AS festival_event,
                       c2.lodging           AS lodging,
                       c2.travel_course     AS travel_course
                  FROM district d2
                  LEFT JOIN counted c2 ON c2.code = d2.code
            ) c
            WHERE d.code = c.code
        """.trimIndent()
    }
}
