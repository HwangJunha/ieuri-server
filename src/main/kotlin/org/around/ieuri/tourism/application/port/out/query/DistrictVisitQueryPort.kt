package org.around.ieuri.tourism.application.port.out.query

/** 시군구 방문 기록의 읽기 포트(SPEC-0030 R6·R15). */
interface DistrictVisitQueryPort {

    /**
     * 이 회원이 방문한 시군구 코드 전체.
     *
     * 칸 목록 조회가 161칸 전부의 방문 여부를 판정해야 하므로 **전량을 한 번에 읽는다** —
     * 칸마다 EXISTS를 던지면 161왕복이 된다. 한 사람의 방문은 최대 252건이라 전량을 올려도 싸다.
     */
    fun findVisitedDistrictCodes(memberId: Long): Set<String>

    /** 존재하는 시군구 코드만 남긴다. 없는 코드를 걸러 DISTRICT_NOT_FOUND로 응답하는 데 쓴다. */
    fun filterExistingDistrictCodes(districtCodes: Collection<String>): Set<String>
}
