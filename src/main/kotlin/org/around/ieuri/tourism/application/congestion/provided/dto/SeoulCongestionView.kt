package org.around.ieuri.tourism.application.congestion.provided.dto

import java.time.LocalDateTime

/** seoulCongestion 응답. 업스트림 장애는 오류가 아니라 [degraded]로 표현된다. */
data class SeoulCongestionView(
    val areas: List<CongestionAreaView>,
    val degraded: Boolean,
    val staleSince: LocalDateTime?,
)
