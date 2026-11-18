package org.around.ieuri.tourism.application.congestion.provided.dto

import org.around.ieuri.tourism.domain.congestion.CommercialStatus
import org.around.ieuri.tourism.domain.congestion.CongestionForecast
import org.around.ieuri.tourism.domain.congestion.CongestionSnapshot

/**
 * 구역 1곳의 실시간 혼잡도. 경계는 [CongestionAreaBoundaryView]에 있고 [id]로 조인한다.
 * - [forecast]·[commercial]의 null은 부재이며 조회 실패가 아니다.
 */
data class CongestionAreaView(
    val id: String,
    val name: String,
    val congestion: CongestionSnapshot,
    val forecast: List<CongestionForecast>?,
    val commercial: CommercialStatus?,
)
