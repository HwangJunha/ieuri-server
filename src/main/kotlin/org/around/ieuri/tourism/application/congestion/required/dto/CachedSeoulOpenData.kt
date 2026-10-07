package org.around.ieuri.tourism.application.congestion.required.dto

import java.time.Instant

/** 캐시된 값과 그 저장 시각. 신선도는 [storedAt]과 최소 재호출 간격으로 판정한다. */
data class CachedSeoulOpenData(
    val snapshot: SeoulOpenDataSnapshot,
    val storedAt: Instant,
)
