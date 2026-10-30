package org.around.ieuri.tourism.application.footprint.required

/** 발자취 읽기(SPEC-0030). 지도 조회와 관계 맺기가 루트를 거치지 않고 읽는다. */
interface FootprintQueryPort {

    /** 이 회원이 방문한 시군구 코드 전체. */
    fun findVisitedDistrictCodes(memberId: Long): Set<String>

    /** 유효한 공유 링크 [token]의 주인. 폐기됐거나 없으면 null이다(둘을 구분하지 않는다). */
    fun findSharerId(token: String): Long?
}
