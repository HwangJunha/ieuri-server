package org.around.ieuri.tourism.application.region.provided.dto

/** 대표 위치 갱신 결과. [cleared]는 이전에 값이 있다가 비운 시군구만 센다. */
data class DistrictRepresentativeRefreshResult(
    val selected: Int,
    val cleared: Int,
)
