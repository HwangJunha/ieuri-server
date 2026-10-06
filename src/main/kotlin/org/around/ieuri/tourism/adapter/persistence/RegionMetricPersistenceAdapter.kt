package org.around.ieuri.tourism.adapter.persistence

import org.around.ieuri.tourism.application.shared.provided.dto.SyncConcentrationRateCommand
import org.around.ieuri.tourism.application.shared.provided.dto.SyncDemandIntensityCommand
import org.around.ieuri.tourism.application.shared.provided.dto.SyncVisitorCountCommand
import org.around.ieuri.tourism.application.shared.required.RegionMetricStorePort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter
import org.springframework.stereotype.Component
import java.sql.Date
import java.sql.PreparedStatement

/**
 * KTO 빅데이터 4종 upsert 어댑터(SPEC-0015 R7). JPA에 이식 가능한 upsert가 없어 생 SQL이다.
 * - `ON CONFLICT`는 컬럼 목록으로 지정한다 — V44의 UNIQUE 정의가 바뀌면 여기도 고쳐야 한다.
 * - 충돌 시 값을 덮어쓴다(업스트림 정정 반영). 한 페이지를 한 번의 배치로 보낸다.
 */
@Component
class RegionMetricPersistenceAdapter(
    private val exposedJdbcTemplate: JdbcTemplate,
) : RegionMetricStorePort {
    override fun upsertVisitorCounts(records: List<SyncVisitorCountCommand>): Int {
        if (records.isEmpty()) return 0
        val setter = ParameterizedPreparedStatementSetter<SyncVisitorCountCommand> { ps: PreparedStatement, it ->
            ps.setString(1, it.districtCode)
            ps.setDate(2, Date.valueOf(it.baseDate))
            ps.setString(3, it.visitorType)
            ps.setBigDecimal(4, it.touristCount)
        }
        return batch(VISITOR_COUNT_SQL, records, setter)
    }
    override fun upsertConcentrationRates(records: List<SyncConcentrationRateCommand>): Int {
        if (records.isEmpty()) return 0
        val setter = ParameterizedPreparedStatementSetter<SyncConcentrationRateCommand> { ps: PreparedStatement, it ->
            ps.setString(1, it.districtCode)
            ps.setString(2, it.touristSpotName)
            ps.setDate(3, Date.valueOf(it.targetDate))
            ps.setBigDecimal(4, it.concentrationRate)
        }
        return batch(CONCENTRATION_RATE_SQL, records, setter)
    }
    override fun upsertStayIntensities(records: List<SyncDemandIntensityCommand>): Int = upsertDemandIntensity(STAY_INTENSITY_SQL, records)
    override fun upsertSpendingIntensities(records: List<SyncDemandIntensityCommand>): Int = upsertDemandIntensity(SPENDING_INTENSITY_SQL, records)

    /** 체류·소비 강도는 컬럼 구성이 같아 SQL만 갈린다. */
    private fun upsertDemandIntensity(sql: String, records: List<SyncDemandIntensityCommand>): Int {
        if (records.isEmpty()) return 0
        val setter = ParameterizedPreparedStatementSetter<SyncDemandIntensityCommand> { ps: PreparedStatement, it ->
            ps.setString(1, it.districtCode)
            ps.setDate(2, Date.valueOf(it.baseMonth))
            ps.setString(3, it.indicatorCode)
            ps.setBigDecimal(4, it.indicatorValue)
        }
        return batch(sql, records, setter)
    }

    /** 반영된 행 수를 센다. */
    private fun <T : Any> batch(sql: String, records: List<T>, setter: ParameterizedPreparedStatementSetter<T>): Int = exposedJdbcTemplate.batchUpdate(sql, records, records.size, setter)
        .sumOf { chunk -> chunk.count { it != 0 } }

    private companion object {
        /** `updated_at`은 값이 실제로 바뀐 경우에만 올린다. */
        val VISITOR_COUNT_SQL = """
            INSERT INTO kto_visitor_count (district_code, base_date, visitor_type, tou_num)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (district_code, base_date, visitor_type) DO UPDATE SET
                tou_num    = EXCLUDED.tou_num,
                updated_at = CASE
                    WHEN kto_visitor_count.tou_num IS DISTINCT FROM EXCLUDED.tou_num
                    THEN now() ELSE kto_visitor_count.updated_at
                END
        """.trimIndent()

        val CONCENTRATION_RATE_SQL = """
            INSERT INTO kto_concentration_rate (district_code, tourist_spot_name, target_date, cnctr_rate)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (district_code, tourist_spot_name, target_date) DO UPDATE SET
                cnctr_rate = EXCLUDED.cnctr_rate,
                updated_at = CASE
                    WHEN kto_concentration_rate.cnctr_rate IS DISTINCT FROM EXCLUDED.cnctr_rate
                    THEN now() ELSE kto_concentration_rate.updated_at
                END
        """.trimIndent()

        val STAY_INTENSITY_SQL = """
            INSERT INTO kto_stay_intensity (district_code, base_month, indicator_cd, indicator_value)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (district_code, base_month, indicator_cd) DO UPDATE SET
                indicator_value = EXCLUDED.indicator_value,
                updated_at      = CASE
                    WHEN kto_stay_intensity.indicator_value IS DISTINCT FROM EXCLUDED.indicator_value
                    THEN now() ELSE kto_stay_intensity.updated_at
                END
        """.trimIndent()

        val SPENDING_INTENSITY_SQL = """
            INSERT INTO kto_spending_intensity (district_code, base_month, indicator_cd, indicator_value)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (district_code, base_month, indicator_cd) DO UPDATE SET
                indicator_value = EXCLUDED.indicator_value,
                updated_at      = CASE
                    WHEN kto_spending_intensity.indicator_value IS DISTINCT FROM EXCLUDED.indicator_value
                    THEN now() ELSE kto_spending_intensity.updated_at
                END
        """.trimIndent()
    }
}
