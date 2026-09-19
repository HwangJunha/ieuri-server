package org.around.ieuri.tourism.application.policy

import java.time.Duration

/**
 * 상세 사전 수집의 정책 값(SPEC-0037). **전부 설정에서 온다**(POL-044) — 재확보 주기 30일은
 * 사용자가 정한 값이고 TourAPI 실측이 아니며, 재시도 간격·횟수도 근거가 되는 문서가 없다.
 *
 * @param refreshAfter 마지막 확보로부터 이 기간이 지나면 다시 확보한다(FR-007).
 * @param retryCount 네트워크 오류 시 재시도 횟수. 첫 시도는 포함하지 않는다(FR-008 = 5).
 * @param retryBaseDelay 첫 재시도까지의 대기. 이후 시도마다 두 배가 된다(FR-008 = 3초).
 * @param concurrency 한 묶음 안에서 동시에 던지는 upstream 호출 수. 초당 한도 응답이 나오면 낮춘다.
 */
data class SpotDetailPrefetchPolicy(
    val refreshAfter: Duration,
    val retryCount: Int,
    val retryBaseDelay: Duration,
    val concurrency: Int,
)
