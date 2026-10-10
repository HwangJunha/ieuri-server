package org.around.ieuri.tourism.domain.region

import org.around.ieuri.tourism.domain.shared.Coordinates

/** 점수를 매길 시군구 하나와 그 지역에서 실제로 값이 있었던 지표들. 결측 지표는 키가 없다(0으로 채우지 않는다, POL-043). */
data class RegionCandidate(
    val code: String,
    val center: Coordinates,
    val metrics: Map<RegionScoreFactor, RegionMetric>,
)
