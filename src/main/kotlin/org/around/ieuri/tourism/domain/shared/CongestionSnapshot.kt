package org.around.ieuri.tourism.domain.shared

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

/** 12개 시간대 예보 중 1건. */
data class CongestionForecast(
    val forecastTime: LocalDateTime,
    val level: CongestionLevel,
    val populationMin: Int,
    val populationMax: Int,
)

/**
 * 구역 1곳의 상업 활성도.
 * - 부재는 이 객체가 null인 것으로, 업스트림 고정 기본값은 [isDefaulted]로 나타낸다.
 */
data class CommercialStatus(
    val level: CommercialActivityLevel,
    val isDefaulted: Boolean,
    val observedAt: LocalDateTime,
)
