package org.around.ieuri.tourism.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 전국 지도 조회 설정(SPEC-0030 §11, POL-044).
 *
 * 스펙이 "출발점이지 확정값이 아니다"라고 명시한 값들이라 코드에 상수로 박지 않는다.
 *
 * @param maxCellSpotPageSize 칸 상세 spot 목록 상한. 초과는 클램프다(POL-018).
 * @param shareUrlBase 공유 링크 앞부분. 기본값은 커스텀 스킴이며, 웹 페이지 개설 후
 *   `https://<도메인>/shared`로 바꾼다 — 앱 배포 없이 전환하려고 설정으로 둔다.
 */
@ConfigurationProperties(prefix = "ieuri.nationwide-map")
data class NationwideMapProperties(
    val maxCellSpotPageSize: Int = 50,
    val shareUrlBase: String = "ieuri://shared",
)
