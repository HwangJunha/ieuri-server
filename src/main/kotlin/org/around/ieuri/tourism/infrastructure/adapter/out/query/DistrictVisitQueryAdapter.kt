package org.around.ieuri.tourism.infrastructure.adapter.out.query

import org.around.ieuri.tourism.application.port.out.query.DistrictVisitQueryPort
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.repository.DistrictJpaRepository
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.repository.MemberDistrictVisitJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 시군구 방문 기록 읽기 어댑터(SPEC-0030 R6·R15).
 *
 * **생 SQL이 없다.** 공간 함수도 upsert도 쓰지 않는 조회라 전부 파생 쿼리로 끝난다.
 * `district` 존재 확인도 코드만 보므로 마찬가지다 — geography 제약은 그 컬럼을 만지는
 * 질의에만 걸린다([MapCellQueryJdbcAdapter]가 `center`를 뽑느라 생 SQL인 것과 갈린다).
 */
@Component
class DistrictVisitQueryAdapter(
    private val repository: MemberDistrictVisitJpaRepository,
    private val districtRepository: DistrictJpaRepository,
) : DistrictVisitQueryPort {

    /**
     * 칸 목록이 161칸 전부의 방문 여부를 판정하므로 **전량을 한 번에 읽는다** — 칸마다
     * EXISTS를 던지면 161왕복이 된다. 한 사람의 방문은 최대 252건이라 전량을 올려도 싸다.
     */
    @Transactional(readOnly = true)
    override fun findVisitedDistrictCodes(memberId: Long): Set<String> =
        repository.findAllByMemberId(memberId).mapTo(mutableSetOf()) { it.districtCode }

    @Transactional(readOnly = true)
    override fun filterExistingDistrictCodes(districtCodes: Collection<String>): Set<String> {
        if (districtCodes.isEmpty()) return emptySet()

        return districtRepository.findAllByCodeIn(districtCodes).mapTo(mutableSetOf()) { it.code }
    }
}
