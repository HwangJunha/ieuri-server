package org.around.ieuri.tourism.application.port.out.query

/** 시군구 방문 기록의 읽기 포트(SPEC-0030). */
interface DistrictVisitQueryPort {

    /** 이 회원이 방문한 시군구 코드 전체. */
    fun findVisitedDistrictCodes(memberId: Long): Set<String>

    /** 존재하는 시군구 코드만 남긴다. 없는 코드를 걸러 DISTRICT_NOT_FOUND로 응답하는 데 쓴다. */
    fun filterExistingDistrictCodes(districtCodes: Collection<String>): Set<String>
}
