package org.around.ieuri.tourism.application.port.out.client

import org.around.ieuri.tourism.application.query.CulturalEventView
import org.around.ieuri.tourism.domain.vo.CommercialStatus
import org.around.ieuri.tourism.domain.vo.CongestionForecast
import org.around.ieuri.tourism.domain.vo.CongestionSnapshot

/**
 * 서울시 실시간 도시데이터 아웃바운드 포트(SPEC-0014 R6~R12).
 *
 * **벌크 호출 변형이 없어 1회 호출당 1곳이 강제된다** — `AREA`에 `ALL`·`*`·공백을 넣어도
 * 여러 구역이 오지 않는다. 일일 쿼터가 1000건이라 121곳 전량 폴링은 성립하지 않으며,
 * 뷰포트에 걸치는 구역만 조회하는 것이 호출량 전략이다(spec §7.4).
 *
 * POL-021은 **우리가 REST를 제공하지 않는다**는 뜻이지 외부 REST를 소비하지 않는다는
 * 뜻이 아니다.
 */
interface SeoulCitydataPort {
    /**
     * 구역 1곳의 실시간 데이터. `citydata` 1회 호출로 인구·상권·문화행사 세 블록을 함께
     * 받는다 — 블록별로 나눠 호출하면 같은 구역에 대해 호출이 배가된다.
     *
     * **업스트림은 코드가 아니라 국문 구역명으로 조회한다.** 그래서 [areaCode]와
     * [areaName]을 함께 받는다 — 이름은 요청에, 코드는 응답 검증에 쓴다.
     *
     * **실패는 예외가 아니라 null이다**(R11). 업스트림 장애로 지도 전체가 실패한 것처럼
     * 다뤄지지 않게 하려는 것이며, 호출자는 null을 degraded로 표현한다.
     *
     * 응답의 구역 코드가 [areaCode]와 다르면 null이다(R15) — 존재하지 않는 구역명을
     * 요청해도 오류 없이 다른 구역 데이터가 오는 동작이 관측됐고, 이 검증이 없으면
     * 잘못된 구역 데이터가 올바른 키로 캐시된다.
     */
    fun fetch(areaCode: String, areaName: String): SeoulCitydataSnapshot?
}

/**
 * 구역 1곳의 업스트림 응답을 블록별로 나눠 담은 결과.
 *
 * **블록마다 갱신 주기가 다르다**(R14) — 인구 5분 / 상권 10분 / 문화행사 1일이며,
 * `CMRCL_TIME`이 야간 내내 정지하는 것이 관측됐다. 그래서 각 블록이 자기 타임스탬프를
 * 들고 다니고, 응답 전체에 단일 TTL을 걸지 않는다.
 *
 * [congestion]이 null이면 인구 블록 파싱에 실패한 것이다(혼잡 단계가 정의된 4값이 아닌
 * 경우 포함, R7). [commercial]·[forecast]의 null은 정상적인 부재다.
 */
data class SeoulCitydataSnapshot(
    val areaCode: String,
    val congestion: CongestionSnapshot?,
    val forecast: List<CongestionForecast>?,
    val commercial: CommercialStatus?,
    val events: List<CulturalEventView>,
)
