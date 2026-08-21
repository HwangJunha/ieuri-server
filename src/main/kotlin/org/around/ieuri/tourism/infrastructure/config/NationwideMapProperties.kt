package org.around.ieuri.tourism.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 전국 지도 조회 설정(SPEC-0030 §11, POL-044).
 *
 * 스펙이 "출발점이지 확정값이 아니다"라고 명시한 값들이라 코드에 상수로 박지 않는다.
 *
 * @param maxCellSpotPageSize 칸 상세 spot 목록 상한. 초과는 클램프다(POL-018).
 */
@ConfigurationProperties(prefix = "ieuri.nationwide-map")
data class NationwideMapProperties(
    val maxCellSpotPageSize: Int = 50,
)
