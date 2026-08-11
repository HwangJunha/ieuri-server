package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import org.around.ieuri.tourism.application.command.SyncClimateIndexCommand
import org.around.ieuri.tourism.application.port.out.persistence.ClimateIndexStorePort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.Date
import java.sql.PreparedStatement
import java.sql.Types

/**
 * 관광기후지수 upsert 어댑터(SPEC-0015 R7 · V49). JPA에 이식 가능한 upsert가 없어 이 어댑터에만
 * 생 SQL이 남는다([TourStatsUpsertJdbcAdapter]와 같은 처리).
 *
 * **기상청 코드 → 행정표준코드 변환을 여기 SQL이 한다.** 매핑 테이블 두 개를 LEFT JOIN하며
 * 순서가 중요하다 — 시군구 매핑이 먼저이고 시도 매핑이 나중이다. 광주 5개 구는 시도 코드
 * `12`를 공유하는 전남과 갈라야 해서, 시도 규칙(`12`→`46`)이 먼저 걸리면 `46xxx`라는 없는
 * 코드가 된다.
 *
 * 어느 매핑에도 걸리지 않으면 기상청 코드를 그대로 넣는다 — V49가 FK를 걸지 않아 무해하며,
 * `district`에 없는 코드는 조회에서 자연히 빠진다.
 */
@Component
class ClimateIndexUpsertJdbcAdapter(
    private val jdbcTemplate: JdbcTemplate,
) : ClimateIndexStorePort {

    @Transactional
    override fun upsertClimateIndices(records: List<SyncClimateIndexCommand>): Int {
        if (records.isEmpty()) return 0
        val setter = ParameterizedPreparedStatementSetter<SyncClimateIndexCommand> { ps: PreparedStatement, it ->
            ps.setString(1, it.districtCode)
            ps.setDate(2, Date.valueOf(it.forecastDate))
            ps.setBigDecimal(3, it.tciValue)
            if (it.tciGrade == null) ps.setNull(4, Types.VARCHAR) else ps.setString(4, it.tciGrade)
        }
        return jdbcTemplate.batchUpdate(UPSERT_SQL, records, records.size, setter)
            .sumOf { chunk -> chunk.count { it != 0 } }
    }

    private companion object {
        /**
         * 매핑 해석 순서: **시군구 매핑 → 시도 매핑(앞 2자리) → 원본 그대로.**
         *
         * `updated_at`은 값이 실제로 바뀐 경우에만 올린다 — 무조건 갱신하면 매일 전 행이 변경된
         * 것처럼 보여 "언제 값이 바뀌었나"를 알 수 없다.
         */
        val UPSERT_SQL = """
            INSERT INTO district_climate_index (district_code, forecast_date, tci_value, tci_grade)
            SELECT
                COALESCE(
                    dm.district_code,
                    CASE WHEN pm.district_code IS NOT NULL
                         THEN pm.district_code || substring(src.kma_code FROM 3)
                    END,
                    src.kma_code
                ),
                src.forecast_date,
                src.tci_value,
                src.tci_grade
            FROM (SELECT ?::varchar AS kma_code, ?::date AS forecast_date,
                         ?::numeric AS tci_value, ?::varchar AS tci_grade) AS src
            LEFT JOIN kma_district_code_map dm ON dm.kma_code = src.kma_code
            LEFT JOIN kma_province_code_map pm
                   ON dm.kma_code IS NULL AND pm.kma_code = substring(src.kma_code FROM 1 FOR 2)
            ON CONFLICT (district_code, forecast_date) DO UPDATE SET
                tci_value  = EXCLUDED.tci_value,
                tci_grade  = EXCLUDED.tci_grade,
                updated_at = CASE
                    WHEN district_climate_index.tci_value IS DISTINCT FROM EXCLUDED.tci_value
                      OR district_climate_index.tci_grade IS DISTINCT FROM EXCLUDED.tci_grade
                    THEN now() ELSE district_climate_index.updated_at
                END
        """.trimIndent()
    }
}
