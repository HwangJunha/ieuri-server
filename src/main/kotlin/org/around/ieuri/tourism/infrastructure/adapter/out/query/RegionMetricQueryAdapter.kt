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
     * 시군구 전건과 각 KTO 테이블의 **최신 1행**.
     *
     * 네 개의 `LATERAL` 서브쿼리가 각각 `(sigungu_code, 날짜 DESC)` 인덱스를 타 시군구당
     * 인덱스 선두 1행만 읽는다(V44). 지표가 없는 시군구는 `LEFT JOIN`이라 값이 null로 오며
     * **0으로 채우지 않는다**(POL-043).
     *
     * 집중률만 관광지 단위 값이라 대상일 기준으로 시군구 평균을 낸다(설계 §3.2).
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

    private companion object {
        val SQL = """
            SELECT s.code,
                   s.name_ko, s.name_en, s.name_ja, s.name_zh,
                   s.province_name_ko, s.province_name_en, s.province_name_ja, s.province_name_zh,
                   ST_Y(s.representative_location::geometry) AS center_lat,
                   ST_X(s.representative_location::geometry) AS center_lng,
                   c.cnctr_rate, c.cnctr_date,
                   v.tou_num, v.visitor_date,
                   st.stay_value, st.stay_month,
                   sp.spending_value, sp.spending_month
            FROM sigungu s
            -- **오늘에 가장 가까운 예측일을 쓴다.** 집중률은 향후 30일치가 함께 오는데,
            -- 사용자는 "지금 어디 갈까"를 묻지 30일 뒤를 묻지 않는다. 가장 먼 날을 고르면
            -- 성수기 해수욕장이 "한산한 곳"으로 뒤집힌다(실측: 속초 오늘 67.4 → 30일 뒤 28.6).
            --
            -- 오늘 이전 행은 제외한다 — 지난 예측은 이미 의미가 없다. 오늘 행이 없으면
            -- 그중 가장 이른 미래를 쓰고, 그마저 없으면 이 지역의 집중률은 결측이다(R14).
            LEFT JOIN LATERAL (
                SELECT AVG(k.cnctr_rate) AS cnctr_rate, k.target_date AS cnctr_date
                FROM kto_concentration_rate k
                WHERE k.sigungu_code = s.code
                  AND k.target_date >= CURRENT_DATE
                GROUP BY k.target_date
                ORDER BY k.target_date ASC
                LIMIT 1
            ) c ON TRUE
            LEFT JOIN LATERAL (
                SELECT k.tou_num, k.base_date AS visitor_date
                FROM kto_visitor_count k
                WHERE k.sigungu_code = s.code
                ORDER BY k.base_date DESC
                LIMIT 1
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
