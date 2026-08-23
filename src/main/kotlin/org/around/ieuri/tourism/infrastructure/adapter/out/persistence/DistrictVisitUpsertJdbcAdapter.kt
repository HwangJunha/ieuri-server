package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import org.around.ieuri.tourism.application.port.out.persistence.DistrictVisitUpsertPort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 방문 기록 등록 전용 어댑터(SPEC-0030 R3).
 *
 * **`INSERT ... ON CONFLICT DO NOTHING`이라 생 SQL이다** — JPA에는 이식 가능한 upsert가 없고,
 * 먼저 조회해 있는지 보는 방식은 동시 요청에서 깨진다(CLAUDE.md의 허용 사유 그대로).
 * 이 클래스에 생 SQL이 격리되며, 삭제·조회는 JPA 파생 쿼리를 쓴다.
 */
@Component
class DistrictVisitUpsertJdbcAdapter(
    private val jdbcTemplate: JdbcTemplate,
) : DistrictVisitUpsertPort {

    /**
     * `RETURNING`으로 **실제로 삽입된 행만** 받는다. 이미 있던 코드는 `DO NOTHING`이라
     * 반환되지 않으므로, 호출자는 이번 요청으로 바뀐 칸만 골라낼 수 있다(계약 §Mutation).
     *
     * 충돌 대상은 자연키 UNIQUE(`uq_member_district_visit`)다 — PK는 대리키라 충돌하지 않는다.
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
