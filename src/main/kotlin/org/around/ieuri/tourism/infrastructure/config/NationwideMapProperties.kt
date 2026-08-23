package org.around.ieuri.tourism.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 전국 지도 조회 설정(SPEC-0030 §11, POL-044).
 *
 * 스펙이 "출발점이지 확정값이 아니다"라고 명시한 값들이라 코드에 상수로 박지 않는다.
 *
 * @param maxCellSpotPageSize 칸 상세 spot 목록 상한. 초과는 클램프다(POL-018).
 * @param shareUrlBase 공유 링크 앞부분. 뒤에 토큰을 붙여 완성한다.
 *   **커스텀 스킴을 쓰지 않는다** — 카카오톡을 비롯한 메신저가 `http`/`https`만 링크로
 *   인식해 `ieuri://`는 눌리지 않는다(실측). 공유가 카톡으로 이뤄지는 이상 딥링크 전용
 *   스킴은 의미가 없어 처음부터 웹 URL로 낸다. 도메인이 바뀔 수 있어 설정으로 둔다.
 */
@ConfigurationProperties(prefix = "ieuri.nationwide-map")
data class NationwideMapProperties(
    val maxCellSpotPageSize: Int = 50,
    val shareUrlBase: String = "https://www.ieuri.co.kr/tour/korea-map",
    val maxRelationPageSize: Int = 50,
)
