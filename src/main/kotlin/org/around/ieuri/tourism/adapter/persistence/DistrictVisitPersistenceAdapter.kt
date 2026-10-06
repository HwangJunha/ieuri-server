package org.around.ieuri.tourism.adapter.persistence

import org.around.ieuri.tourism.adapter.persistence.table.MemberDistrictVisitTable
import org.around.ieuri.tourism.application.visit.required.DistrictVisitRepository
import org.around.ieuri.tourism.domain.visit.DistrictVisits
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/** 시군구 방문 기록 저장소 구현(SPEC-0030 R3·R4 · §9.1). */
@Repository
class DistrictVisitPersistenceAdapter(
    private val exposedJdbcTemplate: JdbcTemplate,
) : DistrictVisitRepository {

    /**
     * 실제로 삽입된 행만 `RETURNING`으로 돌려준다 — 이미 있던 코드는 빠진다. 한 문장이라 raw SQL이다.
     * - 충돌 대상은 자연키 UNIQUE(`uq_member_district_visit`)다.
     */
    override fun record(visits: DistrictVisits): Set<String> {
        if (visits.isEmpty) return emptySet()

        return exposedJdbcTemplate.query(
            """
            INSERT INTO tour.member_district_visit (member_id, district_code)
            SELECT ?, code FROM unnest(?::varchar[]) AS code
            ON CONFLICT (member_id, district_code) DO NOTHING
            RETURNING district_code
            """.trimIndent(),
            { rs, _ -> rs.getString("district_code") },
            visits.memberId,
            visits.districtCodes.toTypedArray(),
        ).toSet()
    }

    override fun remove(memberId: Long, districtCodes: Collection<String>) {
        if (districtCodes.isEmpty()) return

        MemberDistrictVisitTable.deleteWhere {
            (MemberDistrictVisitTable.memberId eq memberId) and (MemberDistrictVisitTable.districtCode inList districtCodes)
        }
    }

    /** 탈퇴 시 호출된다 — 방문 기록은 계정과 함께 사라진다(§9.1). */
    override fun removeAll(memberId: Long): Int = MemberDistrictVisitTable.deleteWhere { MemberDistrictVisitTable.memberId eq memberId }
}
