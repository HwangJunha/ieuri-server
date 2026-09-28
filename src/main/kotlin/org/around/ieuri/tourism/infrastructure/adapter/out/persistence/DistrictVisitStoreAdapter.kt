package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import org.around.ieuri.tourism.application.port.out.persistence.DistrictVisitStorePort
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.repository.MemberDistrictVisitJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 시군구 방문 기록 삭제 어댑터(SPEC-0030 R4 · §9.1). 등록은 [DistrictVisitUpsertJdbcAdapter]가 한다. */
@Component
class DistrictVisitStoreAdapter(
    private val repository: MemberDistrictVisitJpaRepository,
) : DistrictVisitStorePort {

    @Transactional
    override fun remove(memberId: Long, districtCodes: Collection<String>) {
        if (districtCodes.isEmpty()) return

        repository.deleteByMemberIdAndDistrictCodeIn(memberId, districtCodes)
    }

    @Transactional
    override fun removeAll(memberId: Long): Int = repository.deleteByMemberId(memberId)
}
