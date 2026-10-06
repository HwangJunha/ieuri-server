package org.around.ieuri.tourism.application.visit.required

import org.around.ieuri.tourism.domain.visit.DistrictVisits

/**
 * 시군구 방문 기록 저장소(SPEC-0030).
 * - 시군구 코드 외에는 좌표도 시각도 저장하지 않는다(POL-045).
 */
interface DistrictVisitRepository {

    /**
     * 방문으로 기록하고 이번에 실제 추가된 코드를 돌려준다. 이미 기록된 코드는 무시된다.
     * - 존재하지 않는 시군구 코드는 호출자가 먼저 걸러야 한다.
     */
    fun record(visits: DistrictVisits): Set<String>

    /** 방문 기록을 지운다. 기록되지 않은 코드가 섞여 있어도 오류가 아니다. */
    fun remove(memberId: Long, districtCodes: Collection<String>)

    /** 탈퇴 회원의 방문 기록을 전부 지우고 지운 건수를 돌려준다(0이어도 오류 아님). */
    fun removeAll(memberId: Long): Int
}
