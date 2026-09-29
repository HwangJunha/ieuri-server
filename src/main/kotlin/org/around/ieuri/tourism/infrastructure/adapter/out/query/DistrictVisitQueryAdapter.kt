package org.around.ieuri.tourism.infrastructure.adapter.out.query

import org.around.ieuri.tourism.application.port.out.query.DistrictVisitQueryPort
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.repository.DistrictJpaRepository
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.repository.MemberDistrictVisitJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 시군구 방문 기록 읽기 어댑터(SPEC-0030 R6·R15). 전부 파생 쿼리다. */
@Component
class DistrictVisitQueryAdapter(
    private val repository: MemberDistrictVisitJpaRepository,
    private val districtRepository: DistrictJpaRepository,
) : DistrictVisitQueryPort {

    /** 회원의 방문 전량을 한 번에 읽는다(칸마다 묻지 않는다). */
    @Transactional(readOnly = true)
    override fun findVisitedDistrictCodes(memberId: Long): Set<String> = repository.findAllByMemberId(memberId).mapTo(mutableSetOf()) { it.districtCode }

    @Transactional(readOnly = true)
    override fun filterExistingDistrictCodes(districtCodes: Collection<String>): Set<String> {
        if (districtCodes.isEmpty()) return emptySet()

        return districtRepository.findAllByCodeIn(districtCodes).mapTo(mutableSetOf()) { it.code }
    }
}
