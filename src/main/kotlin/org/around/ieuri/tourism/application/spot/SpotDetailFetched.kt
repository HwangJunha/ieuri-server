package org.around.ieuri.tourism.application.spot

import org.around.ieuri.tourism.domain.spot.SpotDetail

/**
 * upstream에서 상세를 가져왔을 때 발행되는 이벤트(SPEC-0004 R-14). 리스너가 DB에 적재한다.
 * - 조회 응답은 적재를 기다리지 않는다(AC-39).
 */
data class SpotDetailFetched(
    val spotId: Long,
    val detail: SpotDetail,
)
