package org.around.ieuri.tourism.adapter.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 전국 지도 조회 설정(SPEC-0030 §11, POL-044).
 *
 * @param maxCellSpotPageSize 칸 상세 spot 목록 상한. 초과는 클램프다(POL-018).
 * @param shareUrlBase 공유 링크 앞부분(뒤에 토큰을 붙인다). 메신저가 커스텀 스킴을 링크로 인식하지 않아 웹 URL이다.
 */
@ConfigurationProperties(prefix = "ieuri.nationwide-map")
data class NationwideMapProperties(
    val maxCellSpotPageSize: Int = 50,
    val shareUrlBase: String = "https://www.ieuri.co.kr/tour/korea-map",
    val maxRelationPageSize: Int = 50,
)
