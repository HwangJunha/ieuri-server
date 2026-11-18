package org.around.ieuri.tourism.application.spot

import org.around.ieuri.tourism.domain.spot.SpotDetail

/**
 * upstream에 상세 본문이 없음을 확인했을 때 발행되는 이벤트(SPEC-0008 R-3).
 * - NOT NULL 컬럼은 [fallback](= `spot` 행의 값)으로 채운다.
 * - upstream 실패 시에는 발행하지 않는다(AC-16·AC-4).
 */
data class SpotDetailAbsent(
    val spotId: Long,
    val fallback: SpotDetail,
)
