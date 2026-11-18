package org.around.ieuri.tourism.application.spot

import java.time.Duration

/**
 * 상세 사전 수집의 정책 값(SPEC-0037). 전부 설정에서 온다(POL-044).
 *
 * @param refreshAfter 마지막 확보로부터 이 기간이 지나면 다시 확보한다(FR-007).
 * @param callsPerSecond 한 갈래가 초당 던지는 upstream 호출 수. 상류 한도는 (언어 × 오퍼레이션)마다 초당 50이다.
 */
data class SpotDetailPrefetchPolicy(
    val refreshAfter: Duration,
    val callsPerSecond: Int,
)
