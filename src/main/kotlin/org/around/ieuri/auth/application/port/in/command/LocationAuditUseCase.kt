package org.around.ieuri.auth.application.port.`in`.command

/**
 * 위치 조회 감사 기록(SPEC-0032 R19~R21, POL-057).
 *
 * 좌표를 인자로 받는 연산이 **누가·어떤 연산을·언제** 셋을 남긴다.
 *
 * **좌표는 어떤 형태로도 넘기지 않는다** — 위도·경도는 물론 시군구·행정구역 어느 단위로도
 * 남기지 않는다. 위치의 *내용*이 빠지므로 이동 경로가 재구성되지 않고, 그것이 POL-045와
 * 양립하는 유일한 근거다. 그래서 이 인터페이스는 **좌표를 받을 자리 자체가 없다.**
 */
interface LocationAuditUseCase {

    /**
     * [operation]을 [memberId]가 호출했음을 남긴다.
     *
     * **기록 실패가 요청을 실패시키지 않는다** — 감사 기록 때문에 `spots`가 실패하면 안 된다.
     * 다만 조용히 버리지도 않는다: 실패는 로그로 남는다.
     *
     * 비로그인 호출자([memberId]가 null)는 남길 "누구"가 없으므로 기록하지 않는다.
     * 스위치가 꺼져 있으면(R21) 아무것도 하지 않는다.
     */
    fun recordAccess(memberId: Long?, operation: String)
}
