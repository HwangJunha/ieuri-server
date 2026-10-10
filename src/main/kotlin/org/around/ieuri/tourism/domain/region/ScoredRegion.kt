package org.around.ieuri.tourism.domain.region

/** 총점까지 산출된 후보 하나. [factors]는 지표 항목 다음 거리 순서다. [total]은 정렬에만 쓴다. */
data class ScoredRegion(
    val code: String,
    val factors: List<RankedFactorScore>,
    val total: RegionTotalScore,
)
