package org.around.ieuri.tourism.adapter.persistence

import org.around.ieuri.tourism.adapter.persistence.mapper.toShareLink
import org.around.ieuri.tourism.adapter.persistence.table.MapShareLinkTable
import org.around.ieuri.tourism.adapter.persistence.table.MemberDistrictVisitTable
import org.around.ieuri.tourism.application.footprint.required.FootprintRepository
import org.around.ieuri.tourism.domain.footprint.Footprint
import org.around.ieuri.tourism.domain.footprint.ShareLink
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository

/** 발자취 저장소 구현(SPEC-0030 R3·R4·R12~R14 · §9.1). */
@Repository
class FootprintPersistenceAdapter : FootprintRepository {

    override fun findByMemberId(memberId: Long): Footprint = Footprint(
        memberId = memberId,
        visitedDistrictCodes = MemberDistrictVisitTable.select(MemberDistrictVisitTable.districtCode)
            .where { MemberDistrictVisitTable.memberId eq memberId }
            .mapTo(mutableSetOf()) { it[MemberDistrictVisitTable.districtCode] },
        shareLink = activeLink(memberId),
    )

    /** 동시 요청이 같은 코드를 넣어도 유일 제약에 걸리지 않는다(`ON CONFLICT DO NOTHING`). */
    override fun addVisits(memberId: Long, districtCodes: Collection<String>) {
        MemberDistrictVisitTable.batchInsert(districtCodes, ignore = true, shouldReturnGeneratedValues = false) {
            this[MemberDistrictVisitTable.memberId] = memberId
            this[MemberDistrictVisitTable.districtCode] = it
        }
    }

    override fun removeVisits(memberId: Long, districtCodes: Collection<String>) {
        if (districtCodes.isEmpty()) return
        MemberDistrictVisitTable.deleteWhere {
            (MemberDistrictVisitTable.memberId eq memberId) and (MemberDistrictVisitTable.districtCode inList districtCodes)
        }
    }

    /**
     * 새 링크는 `ON CONFLICT DO NOTHING`으로 넣고 다시 읽는다 — 동시 발급에서 먼저 들어간 링크가 남는다(부분 UNIQUE).
     * - 토큰 충돌(192비트 난수)도 같은 무시로 삼켜지면 다시 읽은 결과가 없어 실패한다. 실제로는 일어나지 않는다.
     */
    override fun saveShareLink(memberId: Long, link: ShareLink): ShareLink {
        if (link.issuedAt != null) return link
        MapShareLinkTable.insertIgnore {
            it[MapShareLinkTable.memberId] = memberId
            it[MapShareLinkTable.token] = link.token
        }
        return requireNotNull(activeLink(memberId)) { "발급한 공유 링크를 다시 읽지 못했다." }
    }

    override fun revokeShareLink(memberId: Long, link: ShareLink): Boolean = MapShareLinkTable.update({
        (MapShareLinkTable.memberId eq memberId) and (MapShareLinkTable.token eq link.token) and MapShareLinkTable.revokedAt.isNull()
    }) { it[MapShareLinkTable.revokedAt] = CurrentTimestampWithTimeZone } > 0

    /** 탈퇴 시 호출된다 — 링크는 폐기가 아니라 삭제다(§9.1). */
    override fun deleteAll(memberId: Long): Int = MemberDistrictVisitTable.deleteWhere { MemberDistrictVisitTable.memberId eq memberId } +
        MapShareLinkTable.deleteWhere { MapShareLinkTable.memberId eq memberId }

    private fun activeLink(memberId: Long): ShareLink? = MapShareLinkTable.selectAll()
        .where { (MapShareLinkTable.memberId eq memberId) and MapShareLinkTable.revokedAt.isNull() }
        .singleOrNull()?.toShareLink()
}
