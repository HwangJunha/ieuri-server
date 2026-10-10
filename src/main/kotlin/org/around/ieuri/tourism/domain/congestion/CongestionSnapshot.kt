package org.around.ieuri.tourism.domain.congestion

import java.time.LocalDateTime

/**
 * 구역 1곳의 현재 인구밀집 스냅샷(SPEC-0014).
 * - [observedAt]은 조회 시각이 아니라 업스트림 `PPLTN_TIME`(KST 벽시계 시각)이다.
 */
data class CongestionSnapshot(
    val level: CongestionLevel,
    val populationMin: Int,
    val populationMax: Int,
    val observedAt: LocalDateTime,
)
