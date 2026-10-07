package org.around.ieuri.tourism.application.spot.provided

import org.around.ieuri.tourism.application.spot.required.PrefetchTarget
import org.around.ieuri.tourism.application.spot.required.SpotDetailPart
import java.time.Instant

/** 상세 사전 수집의 대상 조회(SPEC-0037). 수집은 상류 호출을 끼고 돌아 트랜잭션 밖이라, 대상만 따로 읽는다. */
interface SpotPrefetchQueryUseCase {
    /** [part] 갈래를 확보해야 할 원본, `spot.id` 오름차순 [limit]건. 행이 없거나 [staleBefore]보다 오래된 것이다. */
    fun findTargets(part: SpotDetailPart, afterSpotId: Long, limit: Int, staleBefore: Instant): List<PrefetchTarget>
}
