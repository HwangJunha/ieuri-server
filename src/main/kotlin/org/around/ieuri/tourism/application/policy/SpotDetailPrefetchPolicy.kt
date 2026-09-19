package org.around.ieuri.tourism.application.policy

import java.time.Duration

/**
 * 상세 사전 수집의 정책 값(SPEC-0037). **전부 설정에서 온다**(POL-044) — 재확보 주기 30일은
 * 사용자가 정한 값이고 TourAPI 실측이 아니며, 동시 호출 수도 초당 한도가 고지되지 않아 근거가 없다.
 *
 * @param refreshAfter 마지막 확보로부터 이 기간이 지나면 다시 확보한다(FR-007).
 * @param concurrency 한 묶음 안에서 동시에 던지는 upstream 호출 수. 초당 한도 응답이 나오면 낮춘다.
 */
data class SpotDetailPrefetchPolicy(
    val refreshAfter: Duration,
    val concurrency: Int,
)
