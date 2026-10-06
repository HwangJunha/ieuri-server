package org.around.ieuri.tourism.adapter.persistence.query

import org.around.ieuri.tourism.adapter.persistence.table.DistrictTable
import org.around.ieuri.tourism.adapter.persistence.table.MemberDistrictVisitTable
import org.around.ieuri.tourism.application.visit.required.DistrictVisitQueryPort
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.stereotype.Component

/** 시군구 방문 기록 읽기 어댑터(SPEC-0030 R6·R15). */
@Component
class DistrictVisitQueryAdapter : DistrictVisitQueryPort {

    /** 회원의 방문 전량을 한 번에 읽는다(칸마다 묻지 않는다). */
    override fun findVisitedDistrictCodes(memberId: Long): Set<String> = MemberDistrictVisitTable.select(MemberDistrictVisitTable.districtCode)
        .where { MemberDistrictVisitTable.memberId eq memberId }
        .mapTo(mutableSetOf()) { it[MemberDistrictVisitTable.districtCode] }
    override fun filterExistingDistrictCodes(districtCodes: Collection<String>): Set<String> {
        if (districtCodes.isEmpty()) return emptySet()

        return DistrictTable.select(DistrictTable.code)
            .where { DistrictTable.code inList districtCodes }
            .mapTo(mutableSetOf()) { it[DistrictTable.code] }
    }
}
