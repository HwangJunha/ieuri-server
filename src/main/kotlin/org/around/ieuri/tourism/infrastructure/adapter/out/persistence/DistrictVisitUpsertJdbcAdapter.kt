package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import org.around.ieuri.tourism.application.port.out.persistence.DistrictVisitUpsertPort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 방문 기록 등록 전용 어댑터(SPEC-0030 R3). `INSERT ... ON CONFLICT DO NOTHING`이라 생 SQL이다. */
@Component
class DistrictVisitUpsertJdbcAdapter(
    private val jdbcTemplate: JdbcTemplate,
) : DistrictVisitUpsertPort {

    /**
     * 실제로 삽입된 행만 `RETURNING`으로 돌려준다 — 이미 있던 코드는 빠진다.
     * - 충돌 대상은 자연키 UNIQUE(`uq_member_district_visit`)다.
     */
    @Transactional
    override fun record(memberId: Long, districtCodes: Collection<String>): Set<String> {
        if (districtCodes.isEmpty()) return emptySet()

        return jdbcTemplate.query(
            """
            INSERT INTO tour.member_district_visit (member_id, district_code)
            SELECT ?, code FROM unnest(?::varchar[]) AS code
            ON CONFLICT (member_id, district_code) DO NOTHING
            RETURNING district_code
            """.trimIndent(),
            { rs, _ -> rs.getString("district_code") },
            memberId,
            districtCodes.toTypedArray(),
        ).toSet()
    }
}
