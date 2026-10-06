package org.around.ieuri.tourism.application.shared

import java.time.Duration

/**
 * 서울 실시간 도시데이터 조회 정책값(SPEC-0014 R6·R13, 설정에서 주입).
 *
 * @param minRefetchInterval 같은 구역을 다시 업스트림에 물어보기까지의 최소 간격(R13).
 * @param maxAreaIds 한 번에 조회할 구역 수 상한(R6, POL-018). 초과는 클램프가 아니라 거절이다(TOO_MANY_AREA_IDS).
 */
data class SeoulOpenDataPolicy(
    val minRefetchInterval: Duration,
    val maxAreaIds: Int,
)
