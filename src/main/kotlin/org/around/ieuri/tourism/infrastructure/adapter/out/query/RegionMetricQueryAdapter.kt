package org.around.ieuri.tourism.infrastructure.adapter.out.query

import org.around.ieuri.tourism.application.port.out.query.RegionMetricQueryPort
import org.around.ieuri.tourism.application.query.RegionMetric
import org.around.ieuri.tourism.application.query.RegionRow
import org.around.ieuri.tourism.domain.vo.AreaName
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet

/**
 * 시군구 추천 지표 조회 어댑터(SPEC-0015 R14·R20).
 *
 * `sigungu`의 geography 컬럼들이 **JPA 엔티티에 매핑되어 있지 않다** — 매핑하면
 * `ddl-auto: validate`가 기동 시 실패한다. 그래서 이 질의는 생 SQL로 남는다
 * (`CongestionAreaQueryAdapter`·`SpotSpatialQueryAdapter`와 같은 선례).
 *
 * **지표마다 질의를 나눈다.** 집계 단위가 서로 달라 하나로 묶으면 LATERAL 네 겹이 되고,
 * 지역과 지표를 잇는 규칙이 SQL 안에 숨는다. 결합은 서비스가 코드로 한다.
 */
@Component
class RegionMetricQueryAdapter(
    private val jdbcTemplate: JdbcTemplate,
) : RegionMetricQueryPort {

    /**
     * 추천 후보 시군구 전건.
     *
     * **좌표는 `center`가 아니라 `representative_location`이다.** `center`는 경계에서 산출한
     * 기하학적 중심이라 산속처럼 spot이 없는 곳일 수 있다. 추천 결과의 좌표는 실제로 갈 곳이
     * 있는 지점이어야 하므로 배치가 골라 둔 대표 위치를 내려보낸다(V46).
     *
     * **`ST_X`/`ST_Y`는 geometry만 받으므로 `::geometry` 캐스팅이 필요하다.** `ST_X`가 경도,
     * `ST_Y`가 위도다 — 뒤집으면 조용히 틀린다.
     */
    @Transactional(readOnly = true)
    override fun findRegions(): List<RegionRow> =
        jdbcTemplate.query(
            """
            SELECT code,
                   name_ko, name_en, name_ja, name_zh,
                   province_name_ko, province_name_en, province_name_ja, province_name_zh,
                   ST_Y(representative_location::geometry) AS center_lat,
                   ST_X(representative_location::geometry) AS center_lng
            FROM sigungu
            -- 대표 위치가 없는 시군구는 후보에서 아예 뺀다. 추천해 놓고 그 지역에 갈 곳이
            -- 없으면 추천이 성립하지 않는다. NULL은 배치가 선정에 실패했다는 뜻이다.
            WHERE representative_location IS NOT NULL
            ORDER BY code
            """.trimIndent(),
        ) { rs, _ ->
            RegionRow(
                code = rs.getString("code"),
                name = rs.areaName(""),
                provinceName = rs.areaName("province_"),
                center = Coordinates(
                    latitude = rs.getDouble("center_lat"),
                    longitude = rs.getDouble("center_lng"),
                ),
            )
        }

    /**
     * 시군구별 **오늘 예측** 집중률의 평균.
     *
     * 집중률은 향후 30일치가 함께 오는데, 사용자는 "지금 어디 갈까"를 묻지 30일 뒤를 묻지
     * 않는다. 먼 날을 고르면 성수기 해수욕장이 "한산한 곳"으로 뒤집힌다(실측: 속초 오늘
     * 67.4 → 30일 뒤 28.6). 오늘 행이 없으면 미래로 밀지 않고 **결측이다**(R14) — 다른 날의
     * 예측을 오늘의 혼잡도로 내놓는 것은 조용히 틀린 값이다.
     *
     * 값이 관광지 단위로 오므로 시군구 점수로 쓰려면 평균으로 묶는다(설계 §3.2).
     */
    @Transactional(readOnly = true)
    override fun findConcentrationRates(): Map<String, RegionMetric> =
        jdbcTemplate.query(
            """
            SELECT sigungu_code, AVG(cnctr_rate) AS value, target_date AS basis_date
            FROM kto_concentration_rate
            WHERE target_date = (now() AT TIME ZONE 'Asia/Seoul')::date
            GROUP BY sigungu_code, target_date
            """.trimIndent(),
        ) { rs, _ -> rs.getString("sigungu_code") to rs.metric() }.toMap()

    /**
     * 시군구별 **최근 7일 평균 방문자수(외지인+외국인)**.
     *
     * 하루에 방문객 구분별로 3행이 있어 한 행만 읽으면 한 구분만 보게 된다. 현지인(1)은
     * 주민의 일상 이동이라 관광 수요가 아니다 — 포함하면 "사람이 사는 곳"이 추천된다
     * (실측: 제주시 현지인 487,659 vs 외지인 146,084).
     *
     * 하루만 보면 요일에 따라 40%씩 흔들린다(실측: 강남구 금 1,312,557 vs 일 939,745). 그래서
     * 그 지역의 최신 기준일부터 7일을 평균한다. 기준일은 지역마다 달라 전역 최댓값이 아니라
     * **지역별 최댓값**에서 되짚는다.
     *
     * 나이(일)는 **DB가 한국 날짜로 계산한다** — 앱에서 재면 앱 시계와 이 질의의 `오늘`이
     * 서로 다른 날을 가리킬 수 있다(DB가 UTC라 한국 00~09시 구간에서 하루가 어긋난다).
     */
    @Transactional(readOnly = true)
    override fun findVisitorCounts(): Map<String, RegionMetric> =
        jdbcTemplate.query(
            """
            WITH daily AS (
                SELECT k.sigungu_code, k.base_date, SUM(k.tou_num) AS day_total
                FROM kto_visitor_count k
                WHERE k.visitor_type IN ('2', '3')
                  AND k.base_date > (
                      SELECT MAX(m.base_date) - INTERVAL '7 days'
                      FROM kto_visitor_count m
                      WHERE m.sigungu_code = k.sigungu_code
                  )
                GROUP BY k.sigungu_code, k.base_date
            )
            SELECT sigungu_code,
                   AVG(day_total) AS value,
                   MAX(base_date) AS basis_date,
                   ((now() AT TIME ZONE 'Asia/Seoul')::date - MAX(base_date)) AS age_days
            FROM daily
            GROUP BY sigungu_code
            """.trimIndent(),
        ) { rs, _ -> rs.getString("sigungu_code") to rs.metric(ageColumn = "age_days") }.toMap()

    /** 시군구별 **최신 월** 체류 강도. 월 단위라 수십 일 지난 값이 정상이며 감쇠하지 않는다. */
    @Transactional(readOnly = true)
    override fun findStayIntensities(): Map<String, RegionMetric> =
        findLatestMonthly("kto_stay_intensity")

    /** 시군구별 **최신 월** 소비 강도. 외지인 소비 기준이며 외국인 소비는 포함하지 않는다. */
    @Transactional(readOnly = true)
    override fun findSpendingIntensities(): Map<String, RegionMetric> =
        findLatestMonthly("kto_spending_intensity")

    /**
     * 월 지표 공통 형태 — 지역마다 자기 최신 월 1행.
     *
     * [table]은 호출부에서만 오는 상수라 바인딩 대상이 아니다. 외부 입력을 여기 넘기면 안 된다.
     */
    private fun findLatestMonthly(table: String): Map<String, RegionMetric> =
        jdbcTemplate.query(
            """
            SELECT DISTINCT ON (sigungu_code)
                   sigungu_code, indicator_value AS value, base_month AS basis_date
            FROM $table
            ORDER BY sigungu_code, base_month DESC
            """.trimIndent(),
        ) { rs, _ -> rs.getString("sigungu_code") to rs.metric() }.toMap()

    private fun ResultSet.metric(ageColumn: String? = null) = RegionMetric(
        rawValue = getDouble("value"),
        basisDate = getDate("basis_date")?.toLocalDate(),
        ageDays = ageColumn?.let { getLong(it).takeIf { _ -> !wasNull() } },
    )

    private fun ResultSet.areaName(prefix: String) = AreaName(
        ko = getString("${prefix}name_ko"),
        en = getString("${prefix}name_en"),
        ja = getString("${prefix}name_ja"),
        zh = getString("${prefix}name_zh"),
    )
}
