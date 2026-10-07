package org.around.ieuri.tourism.application.region.provided.dto

import java.time.YearMonth

/**
 * 한 페이지의 결과. [baseMonth]는 월 지표에서 이번 호출이 쓴(또는 찾은) 기준월이며, 못 찾았으면 null이다.
 * - [skippedUnknownCode]가 크면 정적 시군구 테이블이, [dropped]가 크면 응답 필드명이 낡았다는 신호다.
 */
data class RegionMetricPageResult(
    val written: Int,
    val skippedUnknownCode: Int,
    val dropped: Int,
    val hasMore: Boolean,
    val baseMonth: YearMonth? = null,
)
