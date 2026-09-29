package org.around.ieuri.tourism.application.port.out.client

import org.around.ieuri.tourism.application.query.CulturalEventView
import org.around.ieuri.tourism.domain.vo.CommercialStatus
import org.around.ieuri.tourism.domain.vo.CongestionForecast
import org.around.ieuri.tourism.domain.vo.CongestionSnapshot

/**
 * 서울시 실시간 도시데이터 아웃바운드 포트(SPEC-0014 R6~R12).
 * - 벌크 호출이 없어 1회 호출당 1곳이다. 일일 쿼터가 작아 뷰포트에 걸치는 구역만 조회한다.
 */
interface SeoulOpenDataPort {
    /**
     * 구역 1곳의 실시간 데이터. 1회 호출로 인구·상권·문화행사 세 블록을 함께 받는다.
     * - 업스트림은 국문 구역명([areaName])으로 조회하고, [areaCode]는 응답 검증에 쓴다.
     * - 실패는 예외가 아니라 null이다(R11). 응답의 구역 코드가 [areaCode]와 달라도 null이다(R15).
     */
    fun fetch(areaCode: String, areaName: String): SeoulOpenDataSnapshot?
}

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
