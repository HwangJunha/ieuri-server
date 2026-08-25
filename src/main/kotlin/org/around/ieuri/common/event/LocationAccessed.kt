package org.around.ieuri.common.event

/**
 * 좌표를 인자로 받는 연산이 호출됐다(SPEC-0032 R19, POL-057).
 *
 * **공유 커널에 두는 이유는 발행자가 둘 이상이기 때문이다** — tourism·community가 각자 쏘고
 * auth가 받는다. 어느 한 도메인의 `domain/event`에 두면 나머지가 그 도메인을 알아야 한다.
 *
 * **좌표를 담을 자리가 없는 것이 의도다.** 위도·경도는 물론 시군구·행정구역 어느 단위로도
 * 남기지 않는다(POL-045) — 위치의 *내용*이 빠지므로 이동 경로가 재구성되지 않고, 그것이
 * 이 기록이 성립하는 유일한 근거다.
 *
 * @param memberId 조회한 회원. **비로그인은 발행하지 않는다** — 남길 "누구"가 없다.
 * @param operation 연산명(`spots`·`searchSpots` 등).
 */
data class LocationAccessed(
    val memberId: Long,
    val operation: String,
)
