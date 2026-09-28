package org.around.ieuri.tourism.infrastructure.adapter.out.query

import org.around.ieuri.tourism.application.port.out.query.RegionMetricQueryPort
import org.around.ieuri.tourism.application.query.OriginDistrict
import org.around.ieuri.tourism.application.query.RegionMetric
import org.around.ieuri.tourism.application.query.RegionRow
import org.around.ieuri.tourism.domain.vo.AreaName
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet

/**
 * 시군구 추천 지표 조회 어댑터(SPEC-0015 R14·R21).
 * - `district`의 geography 컬럼이 JPA에 매핑되지 않아 생 SQL이다.
 * - 지표마다 질의를 나누고, 지역과 지표의 결합은 서비스가 한다.
 */
@Component
class RegionMetricQueryAdapter(
    private val jdbcTemplate: JdbcTemplate,
) : RegionMetricQueryPort {

    /**
     * origin 좌표를 품은 시군구 1건. 경계 안에 없으면(바다·해외) null이다.
     * - `ST_MakePoint`는 (경도, 위도) 순서다 — 뒤집으면 조용히 0건이 된다.
     */
    @Transactional(readOnly = true)
    override fun findDistrictAt(origin: Coordinates): OriginDistrict? = jdbcTemplate.query(
        """
            SELECT code, area_cd AS province_code
            FROM district
            WHERE ST_Intersects(boundary, ST_MakePoint(?, ?)::geography)
            LIMIT 1
        """.trimIndent(),
        { rs, _ ->
            OriginDistrict(
                code = rs.getString("code"),
                provinceCode = rs.getString("province_code"),
            )
        },
        origin.longitude,
        origin.latitude,
    ).firstOrNull()

    /**
     * 한 시도의 추천 후보 시군구 전건. 좌표는 `center`가 아니라 `representative_location`이다.
     * - `ST_X`가 경도, `ST_Y`가 위도다 — 뒤집으면 조용히 틀린다.
     */
    @Transactional(readOnly = true)
    override fun findRegions(provinceCode: String): List<RegionRow> = jdbcTemplate.query(
        """
            SELECT d.code, d.area_cd AS province_code,
                   d.name_ko, d.name_en, d.name_ja, d.name_zh,
                   a.name_ko AS province_name_ko, a.name_en AS province_name_en,
                   a.name_ja AS province_name_ja, a.name_zh AS province_name_zh,
                   ST_Y(d.representative_location::geometry) AS center_lat,
                   ST_X(d.representative_location::geometry) AS center_lng
            FROM district d
            JOIN area a ON a.area_cd = d.area_cd
            WHERE d.area_cd = ?
            -- 대표 위치가 없는 시군구는 후보에서 아예 뺀다. 추천해 놓고 그 지역에 갈 곳이
            -- 없으면 추천이 성립하지 않는다. NULL은 배치가 선정에 실패했다는 뜻이다.
              AND d.representative_location IS NOT NULL
            ORDER BY d.code
        """.trimIndent(),
        { rs, _ ->
            RegionRow(
                code = rs.getString("code"),
                provinceCode = rs.getString("province_code"),
                name = rs.areaName(""),
                provinceName = rs.areaName("province_"),
                center = Coordinates(
                    latitude = rs.getDouble("center_lat"),
                    longitude = rs.getDouble("center_lng"),
                ),
            )
        },
        provinceCode,
    )

    /**
     * 그 시도 시군구별 오늘 예측 집중률의 평균.
     * - 오늘 행이 없으면 다른 날로 밀지 않고 결측이다(R14).
     */
    @Transactional(readOnly = true)
    override fun findConcentrationRates(provinceCode: String): Map<String, RegionMetric> = jdbcTemplate.query(
        """
            SELECT k.district_code, AVG(k.cnctr_rate) AS value
            FROM kto_concentration_rate k
            JOIN district d ON d.code = k.district_code
            WHERE d.area_cd = ?
              AND k.target_date = (now() AT TIME ZONE 'Asia/Seoul')::date
            GROUP BY k.district_code
        """.trimIndent(),
        { rs, _ -> rs.getString("district_code") to rs.metric() },
        provinceCode,
    ).toMap()

    /**
     * 그 시도 시군구별 최근 7일 평균 방문자수(외지인+외국인, 현지인 제외).
     * - 7일은 지역별 최신 기준일에서 되짚는다. 나이(일)는 DB가 한국 날짜로 계산한다.
     */
    @Transactional(readOnly = true)
    override fun findVisitorCounts(provinceCode: String): Map<String, RegionMetric> = jdbcTemplate.query(
        """
            WITH daily AS (
                SELECT k.district_code, k.base_date, SUM(k.tou_num) AS day_total
                FROM kto_visitor_count k
                JOIN district d ON d.code = k.district_code
                WHERE d.area_cd = ?
                  AND k.visitor_type IN ('2', '3')
                  AND k.base_date > (
                      SELECT MAX(m.base_date) - INTERVAL '7 days'
                      FROM kto_visitor_count m
                      WHERE m.district_code = k.district_code
                  )
                GROUP BY k.district_code, k.base_date
            )
            SELECT district_code,
                   AVG(day_total) AS value,
                   ((now() AT TIME ZONE 'Asia/Seoul')::date - MAX(base_date)) AS age_days
            FROM daily
            GROUP BY district_code
        """.trimIndent(),
        { rs, _ -> rs.getString("district_code") to rs.metric(ageColumn = "age_days") },
        provinceCode,
    ).toMap()

    /** 그 시도 시군구별 최신 월 체류 강도. 월 단위라 수십 일 지난 값이 정상이다. */
    @Transactional(readOnly = true)
    override fun findStayIntensities(provinceCode: String): Map<String, RegionMetric> = findLatestMonthly("kto_stay_intensity", provinceCode)

    /** 그 시도 시군구별 최신 월 소비 강도. 외지인 소비 기준이며 외국인 소비는 포함하지 않는다. */
    @Transactional(readOnly = true)
    override fun findSpendingIntensities(provinceCode: String): Map<String, RegionMetric> = findLatestMonthly("kto_spending_intensity", provinceCode)

    /**
     * 월 지표 공통 형태 — 그 시도의 지역마다 자기 최신 월 1행.
     * - [table]은 문자열 결합이라 호출부 상수만 넘긴다. [provinceCode]는 바인딩한다.
     */
    private fun findLatestMonthly(table: String, provinceCode: String): Map<String, RegionMetric> = jdbcTemplate.query(
        """
            SELECT DISTINCT ON (k.district_code)
                   k.district_code, k.indicator_value AS value
            FROM $table k
            JOIN district d ON d.code = k.district_code
            WHERE d.area_cd = ?
            ORDER BY k.district_code, k.base_month DESC
        """.trimIndent(),
        { rs, _ -> rs.getString("district_code") to rs.metric() },
        provinceCode,
    ).toMap()

    private fun ResultSet.metric(ageColumn: String? = null) = RegionMetric(
        rawValue = getDouble("value"),
        ageDays = ageColumn?.let { getLong(it).takeIf { _ -> !wasNull() } },
    )

    private fun ResultSet.areaName(prefix: String) = AreaName(
        ko = getString("${prefix}name_ko"),
        en = getString("${prefix}name_en"),
        ja = getString("${prefix}name_ja"),
        zh = getString("${prefix}name_zh"),
    )
}
