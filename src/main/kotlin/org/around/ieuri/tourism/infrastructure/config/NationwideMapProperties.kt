package org.around.ieuri.tourism.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 전국 지도 조회 설정(SPEC-0030 §11, POL-044).
 *
 * 스펙이 "출발점이지 확정값이 아니다"라고 명시한 값들이라 코드에 상수로 박지 않는다.
 *
 * @param maxResolveCoordinates 좌표 판정 요청당 상한. 격자 축약 후 남는 좌표가 대개 수백 건이라
 *   500이면 1~2회 요청으로 끝난다. **초과는 클램프가 아니라 거절이다**(§policy 참조).
 * @param maxCellSpotPageSize 칸 상세 spot 목록 상한. 초과는 클램프다(POL-018).
 */
@ConfigurationProperties(prefix = "ieuri.nationwide-map")
data class NationwideMapProperties(
    val maxResolveCoordinates: Int = 500,
    val maxCellSpotPageSize: Int = 50,
)
