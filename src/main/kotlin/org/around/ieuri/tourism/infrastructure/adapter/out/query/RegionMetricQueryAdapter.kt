package org.around.ieuri.tourism.infrastructure.adapter.out.query

import org.around.ieuri.tourism.application.port.out.query.RegionMetricQueryPort
import org.around.ieuri.tourism.application.query.RegionMetricRow
import org.around.ieuri.tourism.domain.vo.AreaName
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.time.LocalDate

/**
 * 시군구 추천 지표 조회 어댑터(SPEC-0015 R14·R20).
 *
 * `sigungu`의 geography 컬럼들이 **JPA 엔티티에 매핑되어 있지 않다** — 매핑하면
 * `ddl-auto: validate`가 기동 시 실패한다. 그래서 이 질의는 생 SQL로 남는다
 * (`CongestionAreaQueryAdapter`·`SpotSpatialQueryAdapter`와 같은 선례).
 *
 * **좌표는 `center`가 아니라 `representative_location`을 쓴다.** `center`는 경계에서 산출한
 * 기하학적 중심이라 산속처럼 spot이 없는 곳일 수 있다. 추천 결과의 좌표는 실제로 갈 곳이
 * 있는 지점이어야 하므로 배치가 골라 둔 대표 위치를 내려보낸다(V46).
 *
 * **`ST_X`/`ST_Y`는 geometry만 받으므로 `::geometry` 캐스팅이 필요하다.** 캐스팅 없이 부르면
 * 함수를 찾지 못해 실패한다. `ST_X`가 경도, `ST_Y`가 위도다 — 뒤집으면 조용히 틀린다.
 */
@Component
class RegionMetricQueryAdapter(
    private val jdbcTemplate: JdbcTemplate,
) : RegionMetricQueryPort {

    /**
     * 시군구 전건과 각 KTO 지표.
     *
     * 지표마다 집계 단위가 다르다 — 집중률은 **오늘 예측**의 시군구 평균(관광지 단위 값이라
     * 묶는다, 설계 §3.2), 방문자수는 **최근 7일 평균(외지인+외국인)**, 월 지표는 최신 1개월이다.
     *
     * 지표가 없는 시군구는 `LEFT JOIN`이라 값이 null로 오며 **0으로 채우지 않는다**(POL-043).
     * 집계만 있는 LATERAL은 행이 없어도 NULL 1행을 돌려주므로 결측이 그대로 null이 된다.
     */
    @Transactional(readOnly = true)
    override fun findLatestMetrics(): List<RegionMetricRow> =
        jdbcTemplate.query(SQL) { rs, _ -> rs.toRow() }

    private fun ResultSet.toRow() = RegionMetricRow(
        code = getString("code"),
        name = AreaName(
            ko = getString("name_ko"),
            en = getString("name_en"),
            ja = getString("name_ja"),
            zh = getString("name_zh"),
        ),
        provinceName = AreaName(
            ko = getString("province_name_ko"),
            en = getString("province_name_en"),
            ja = getString("province_name_ja"),
            zh = getString("province_name_zh"),
        ),
        center = Coordinates(latitude = getDouble("center_lat"), longitude = getDouble("center_lng")),
        concentrationRate = nullableDouble("cnctr_rate"),
        concentrationBasisDate = nullableDate("cnctr_date"),
        visitorCount = nullableDouble("tou_num"),
        visitorBasisDate = nullableDate("visitor_date"),
        visitorAgeDays = nullableLong("visitor_age_days"),
        stayIntensity = nullableDouble("stay_value"),
        stayBasisMonth = nullableDate("stay_month"),
        spendingIntensity = nullableDouble("spending_value"),
        spendingBasisMonth = nullableDate("spending_month"),
    )

    /** `getDouble`은 SQL NULL을 0.0으로 돌려주므로 `wasNull`로 결측을 살려낸다. */
    private fun ResultSet.nullableDouble(column: String): Double? =
        getDouble(column).takeIf { !wasNull() }

    private fun ResultSet.nullableDate(column: String): LocalDate? =
        getDate(column)?.toLocalDate()

    private fun ResultSet.nullableLong(column: String): Long? =
        getLong(column).takeIf { !wasNull() }

    private companion object {
        val SQL = """
            SELECT s.code,
                   s.name_ko, s.name_en, s.name_ja, s.name_zh,
                   s.province_name_ko, s.province_name_en, s.province_name_ja, s.province_name_zh,
                   ST_Y(s.representative_location::geometry) AS center_lat,
                   ST_X(s.representative_location::geometry) AS center_lng,
                   c.cnctr_rate, c.cnctr_date,
                   v.tou_num, v.visitor_date,
                   -- 지표 나이(일). **한국 날짜 기준으로 DB가 계산한다** — 앱에서 재면
                   -- 앱 시계와 이 질의의 `오늘`이 서로 다른 날을 가리킬 수 있다(DB가 UTC라
                   -- 한국 00~09시 구간에서 하루가 어긋난다).
                   ((now() AT TIME ZONE 'Asia/Seoul')::date - v.visitor_date) AS visitor_age_days,
                   st.stay_value, st.stay_month,
                   sp.spending_value, sp.spending_month
            FROM sigungu s
            -- **오늘 예측만 쓴다.** 집중률은 향후 30일치가 함께 오는데, 사용자는 "지금 어디
            -- 갈까"를 묻지 30일 뒤를 묻지 않는다. 먼 날을 고르면 성수기 해수욕장이 "한산한
            -- 곳"으로 뒤집힌다(실측: 속초 오늘 67.4 → 30일 뒤 28.6).
            --
            -- 오늘 행이 없으면 미래로 밀지 않고 **결측이다**(R14) — 다른 날의 예측을 오늘의
            -- 혼잡도로 내놓는 것은 조용히 틀린 값이다. 결측 항목은 그 지역 점수에서 빠진다.
            LEFT JOIN LATERAL (
                SELECT AVG(k.cnctr_rate) AS cnctr_rate, k.target_date AS cnctr_date
                FROM kto_concentration_rate k
                WHERE k.sigungu_code = s.code
                  AND k.target_date = (now() AT TIME ZONE 'Asia/Seoul')::date
                GROUP BY k.target_date
            ) c ON TRUE
            -- **최근 7일 평균이며 외지인(2)+외국인(3)만 합산한다.**
            --
            -- 하루에 방문객 구분별로 3행이 있어 한 행만 읽으면 한 구분만 보게 된다. 현지인(1)은
            -- 주민의 일상 이동이라 관광 수요가 아니다 — 포함하면 "사람이 사는 곳"이 추천된다
            -- (실측: 제주시 현지인 487,659 vs 외지인 146,084).
            --
            -- 하루만 보면 요일에 따라 40%씩 흔들린다(실측: 강남구 금 1,312,557 vs 일 939,745).
            -- 그래서 그 지역의 최신 기준일부터 7일을 평균한다. 기준일은 지역마다 다를 수 있어
            -- 전역 최댓값이 아니라 **지역별 최댓값**에서 되짚는다.
            LEFT JOIN LATERAL (
                SELECT AVG(d.day_total) AS tou_num, MAX(d.base_date) AS visitor_date
                FROM (
                    SELECT k.base_date, SUM(k.tou_num) AS day_total
                    FROM kto_visitor_count k
                    WHERE k.sigungu_code = s.code
                      AND k.visitor_type IN ('2', '3')
                      AND k.base_date > (
                          SELECT MAX(m.base_date) - INTERVAL '7 days'
                          FROM kto_visitor_count m
                          WHERE m.sigungu_code = s.code
                      )
                    GROUP BY k.base_date
                ) d
            ) v ON TRUE
            LEFT JOIN LATERAL (
                SELECT k.indicator_value AS stay_value, k.base_month AS stay_month
                FROM kto_stay_intensity k
                WHERE k.sigungu_code = s.code
                ORDER BY k.base_month DESC
                LIMIT 1
            ) st ON TRUE
            LEFT JOIN LATERAL (
                SELECT k.indicator_value AS spending_value, k.base_month AS spending_month
                FROM kto_spending_intensity k
                WHERE k.sigungu_code = s.code
                ORDER BY k.base_month DESC
                LIMIT 1
            ) sp ON TRUE
            -- 대표 위치가 없는 시군구는 후보에서 아예 뺀다. 추천해 놓고 그 지역에 갈 곳이
            -- 없으면 추천이 성립하지 않는다. NULL은 배치가 선정에 실패했다는 뜻이다.
            WHERE s.representative_location IS NOT NULL
            ORDER BY s.code
        """.trimIndent()
    }
}
