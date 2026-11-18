package org.around.ieuri.tourism.domain.congestion

import java.time.LocalDateTime

/**
 * 구역 1곳의 상업 활성도.
 * - 부재는 이 객체가 null인 것으로, 업스트림 고정 기본값은 [isDefaulted]로 나타낸다.
 */
data class CommercialStatus(
    val level: CommercialActivityLevel,
    val isDefaulted: Boolean,
    val observedAt: LocalDateTime,
)
