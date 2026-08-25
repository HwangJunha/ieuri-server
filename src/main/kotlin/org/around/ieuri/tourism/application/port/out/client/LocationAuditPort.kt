package org.around.ieuri.tourism.application.port.out.client

/**
 * 위치 조회 사실을 감사 기록에 남긴다(SPEC-0032 R19, POL-057).
 *
 * 기록의 소유자는 auth이지만 tourism이 그 도메인의 이름을 알면 추출이 불가능해지므로,
 * 자기 포트를 두고 어댑터가 상대 도메인의 `port/in`으로 번역한다.
 *
 * **좌표를 받을 자리가 없는 것이 의도다** — 위도·경도는 물론 시군구·행정구역 어느 단위로도
 * 남기지 않는다(POL-045). 남기는 것은 누가·어떤 연산을·언제 셋뿐이다.
 */
interface LocationAuditPort {

    /**
     * [memberId]가 [operation]을 호출했음을 남긴다. 비로그인(null)은 기록하지 않는다.
     *
     * **기록 실패가 요청을 실패시키지 않는다** — 구현이 삼키고 로그로 남긴다.
     */
    fun recordAccess(memberId: Long?, operation: String)
}
