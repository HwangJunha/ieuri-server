package org.around.ieuri.tourism.application.policy

import java.time.Duration

/**
 * 서울 실시간 도시데이터 조회 정책값(SPEC-0014 R6·R13, 설정에서 주입).
 *
 * @param minRefetchInterval 같은 구역을 다시 업스트림에 물어보기까지의 최소 간격(R13).
 *   **데이터가 약 30분 지연되어 도착하므로**(spec §4.2) 5분 경계 TTL은 같은 데이터를
 *   6번 재호출한다. 기본값 5분은 spec §12의 출발점이며 실측 후 크게 잡아야 호출량이 준다
 *   — `[TBD]`다.
 * @param maxAreaIds 한 번에 조회할 구역 수 상한(R6, POL-018). 벌크 호출이 없어 구역 수가
 *   그대로 호출 수라, 쿼터 보호를 위해 **클램프가 아니라 거절**이다(TOO_MANY_AREA_IDS).
 *   기본값 20은 지도 한 화면에 걸치는 구역 수를 넉넉히 덮는다 — `[TBD]`다.
 */
data class SeoulOpenDataPolicy(
    val minRefetchInterval: Duration,
    val maxAreaIds: Int,
)
