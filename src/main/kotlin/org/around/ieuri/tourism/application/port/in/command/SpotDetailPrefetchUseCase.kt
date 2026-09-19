package org.around.ieuri.tourism.application.port.`in`.command

import org.around.ieuri.tourism.application.port.out.query.SpotDetailPart

/**
 * 관광지 상세의 사전 전량 수집(SPEC-0037). 사용자의 조회를 기다리지 않고 미리 확보한다.
 *
 * 배치가 한 묶음씩 부르며, 커서와 진행 카운트는 호출자(Tasklet)가 들고 있다 — 유스케이스는
 * 상태를 갖지 않는다.
 */
interface SpotDetailPrefetchUseCase {
    /**
     * [part] 갈래에서 [afterSpotId] 다음 [limit]건을 확보해 적재한다.
     *
     * **한도 도달은 예외가 아니라 결과다** — [PrefetchResult.dailyLimitReached]가 true면 호출자가
     * 그 갈래를 오늘 중단한다(AC-018). 개별 장소의 실패는 세기만 하고 넘어가며, 행이 남지 않아
     * 다음 실행이 다시 집는다(FR-009·AC-014).
     */
    fun prefetchNext(part: SpotDetailPart, afterSpotId: Long, limit: Int): PrefetchResult
}

/**
 * 한 묶음의 처리 결과.
 *
 * [processed]가 0이면 그 갈래는 소진이다 — **성공 건수가 아니라 처리 건수로 판정해야 한다.**
 * 한 묶음이 전부 실패일 때 성공 수로 보면 소진으로 오판해 나머지를 통째로 건너뛴다.
 *
 * [dailyLimitReached]가 true면 호출자가 그 갈래를 오늘 중단한다. **커서를 되돌리지는 않는다** —
 * 다음 실행은 새 `JobInstance`라 커서가 어차피 0부터이고, 확보된 행은 대상 쿼리가 걸러 내므로
 * 건너뛴 장소가 다시 대상이 된다. 재개는 커서가 아니라 데이터가 해결한다.
 */
data class PrefetchResult(
    val processed: Int,
    val fetched: Int,
    val empty: Int,
    val failed: Int,
    /** 한도에 막혀 시도조차 못 한 건수. 이것까지 더해야 [processed]와 맞는다. */
    val skipped: Int,
    /**
     * 이 묶음이 실제로 upstream을 부른 횟수. **재시도까지 센다** — 호출자가 일일 예산을 지키려면
     * 시도한 수가 아니라 소모한 수를 알아야 한다. 카테고리 코드가 없어 생략된 건은 포함되지 않는다.
     */
    val calls: Int,
    val lastSpotId: Long,
    val dailyLimitReached: Boolean = false,
)
