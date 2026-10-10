package org.around.ieuri.tourism.application.congestion.required.dto

import org.around.ieuri.tourism.application.congestion.provided.dto.CulturalEventView
import org.around.ieuri.tourism.domain.congestion.CommercialStatus
import org.around.ieuri.tourism.domain.congestion.CongestionForecast
import org.around.ieuri.tourism.domain.congestion.CongestionSnapshot

/**
 * 구역 1곳의 업스트림 응답을 블록별로 나눠 담은 결과. 블록마다 자기 타임스탬프를 든다(R14).
 * - [congestion]이 null이면 인구 블록 파싱 실패다(혼잡 단계가 정의된 4값 밖인 경우 포함, R7).
 * - [commercial]·[forecast]의 null은 정상적인 부재다.
 */
data class SeoulOpenDataSnapshot(
    val areaCode: String,
    val congestion: CongestionSnapshot?,
    val forecast: List<CongestionForecast>?,
    val commercial: CommercialStatus?,
    val events: List<CulturalEventView>,
)
