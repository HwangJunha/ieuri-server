package org.around.ieuri.tourism.domain.region

/**
 * 한 항목의 정규화 이전 원본 값.
 * - [ageDays]는 DB가 한국 날짜로 계산한 경과 일수다. 방문자수만 채우며, null이면 감쇠하지 않는다.
 */
data class RegionMetric(
    val rawValue: Double,
    val ageDays: Long? = null,
)
