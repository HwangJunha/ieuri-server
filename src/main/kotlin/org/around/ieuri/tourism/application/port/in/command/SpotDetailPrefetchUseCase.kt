package org.around.ieuri.tourism.application.port.`in`.command

import org.around.ieuri.tourism.application.port.out.query.SpotDetailPart

/**
 * 관광지 상세의 사전 전량 수집(SPEC-0037). 사용자의 조회를 기다리지 않고 미리 확보한다.
 *
 * 배치가 한 묶음씩 부르며 커서와 진행은 호출자가 들고 있다 — 유스케이스는 상태를 갖지 않는다.
 */
interface SpotDetailPrefetchUseCase {
    /**
     * [part] 갈래에서 [afterSpotId] 다음 [limit]건을 확보해 적재한다.
     *
     * 개별 장소의 실패는 세기만 하고 넘어간다 — 실패하면 아무것도 쓰지 않아 다음 실행이 다시
     * 집는다(BR-001·FR-009).
     *
     * **일일 한도는 예외로 올린다**(`UpstreamRateLimitedException`). 그날이 끝났다는 뜻이라 이
     * 묶음만의 문제가 아니고, 호출자가 그 갈래를 멈춰야 한다(AC-018).
     */
    fun prefetchNext(part: SpotDetailPart, afterSpotId: Long, limit: Int): PrefetchResult
}

/**
 * 한 묶음의 처리 결과.
 *
 * [processed]가 0이면 그 갈래는 소진이다 — **성공 건수가 아니라 처리 건수로 판정해야 한다.**
 * 한 묶음이 전부 실패일 때 성공 수로 보면 소진으로 오판해 나머지를 통째로 건너뛴다.
 */
data class PrefetchResult(
    val processed: Int,
    val failed: Int,
    val lastSpotId: Long,
)
