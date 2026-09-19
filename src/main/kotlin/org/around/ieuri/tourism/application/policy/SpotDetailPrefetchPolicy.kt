package org.around.ieuri.tourism.application.policy

import java.time.Duration

/**
 * 상세 사전 수집의 정책 값(SPEC-0037). **전부 설정에서 온다**(POL-044) — 재확보 주기 30일은
 * 사용자가 정한 값이고, 초당 호출 수는 실측 경계(50)에 마진을 둔 값이다.
 *
 * @param refreshAfter 마지막 확보로부터 이 기간이 지나면 다시 확보한다(FR-007).
 * @param callsPerSecond 한 갈래가 초당 던지는 upstream 호출 수. 한도는 (언어 × 오퍼레이션)
 *   조합마다 초당 50이며, 통신 순단 뒤 재시도가 몰릴 여지를 두고 그보다 낮게 잡는다.
 */
data class SpotDetailPrefetchPolicy(
    val refreshAfter: Duration,
    val callsPerSecond: Int,
)
