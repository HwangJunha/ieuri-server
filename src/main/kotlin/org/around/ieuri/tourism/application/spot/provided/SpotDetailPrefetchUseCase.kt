package org.around.ieuri.tourism.application.spot.provided

import org.around.ieuri.tourism.application.spot.provided.dto.PrefetchResult
import org.around.ieuri.tourism.application.spot.provided.dto.SpotDetailPart

/** 관광지 상세의 사전 전량 수집(SPEC-0037). 커서와 진행은 호출자가 들고 있다. */
interface SpotDetailPrefetchUseCase {
    /**
     * [part] 갈래에서 [afterSpotId] 다음 [limit]건을 확보해 적재한다.
     * - 개별 장소의 실패는 세기만 하고 아무것도 쓰지 않는다 — 다음 실행이 다시 집는다.
     * - 일일 한도는 `UpstreamRateLimitedException`으로 올린다. 호출자가 그 갈래를 멈춰야 한다.
     */
    fun prefetchNext(part: SpotDetailPart, afterSpotId: Long, limit: Int): PrefetchResult
}
