package org.around.ieuri.tourism.adapter.persistence.query

import org.around.ieuri.tourism.adapter.persistence.table.MapShareLinkTable
import org.around.ieuri.tourism.adapter.persistence.table.MemberDistrictVisitTable
import org.around.ieuri.tourism.application.footprint.required.FootprintQueryPort
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.stereotype.Component

/** 발자취 읽기 어댑터(SPEC-0030 R6·R13·R15). */
@Component
class FootprintQueryAdapter : FootprintQueryPort {

    /** 회원의 방문 전량을 한 번에 읽는다(칸마다 묻지 않는다). */
    override fun findVisitedDistrictCodes(memberId: Long): Set<String> = MemberDistrictVisitTable.select(MemberDistrictVisitTable.districtCode)
        .where { MemberDistrictVisitTable.memberId eq memberId }
        .mapTo(mutableSetOf()) { it[MemberDistrictVisitTable.districtCode] }

    override fun findSharerId(token: String): Long? = MapShareLinkTable.select(MapShareLinkTable.memberId)
        .where { (MapShareLinkTable.token eq token) and MapShareLinkTable.revokedAt.isNull() }
        .singleOrNull()?.get(MapShareLinkTable.memberId)
}
