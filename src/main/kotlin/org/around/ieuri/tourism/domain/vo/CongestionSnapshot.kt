package org.around.ieuri.tourism.domain.vo

import java.time.LocalDateTime

/**
 * 구역 1곳의 현재 인구밀집 스냅샷(SPEC-0014 R7·R8).
 *
 * [observedAt]은 업스트림 `PPLTN_TIME`이며 **데이터가 약 30분 지연되어 도착한다** — 조회
 * 시각이 아니라 이 값이 기준이다. 클라이언트는 "지금"이 아니라 "N시 M분 기준"으로 표시한다.
 *
 * 업스트림이 시간대를 주지 않아 `LocalDateTime`이다(KST 벽시계 시각).
 */
data class CongestionSnapshot(
    val level: CongestionLevel,
    val populationMin: Int,
    val populationMax: Int,
    val observedAt: LocalDateTime,
)

/**
 * 12개 시간대 예보 중 1건(R9). 혼잡도와 같은 4값 enum을 쓴다.
 *
 * 업스트림 `FCST_YN=N`이면 예보 자체가 부재이며 **부재는 조회 실패가 아니다**.
 */
data class CongestionForecast(
    val forecastTime: LocalDateTime,
    val level: CongestionLevel,
    val populationMin: Int,
    val populationMax: Int,
)

/**
 * 구역 1곳의 상업 활성도(R10).
 *
 * 3상태 중 둘을 여기서 표현한다 — **부재**는 이 객체 자체가 null인 것으로, **기본값**은
 * [isDefaulted]로 나타낸다. 나머지 하나가 실측이다.
 *
 * 업스트림이 기준선 없는 39곳에 `보통 시간대`를 고정 반환하므로, 이 구분이 없으면 가짜
 * "보통"이 실측값과 섞인다.
 */
data class CommercialStatus(
    val level: CommercialActivityLevel,
    val isDefaulted: Boolean,
    val observedAt: LocalDateTime,
)
