package org.around.ieuri.tourism.domain.region

/** 한 항목의 점수와, 그 항목이 산출된 후보들 안에서의 순위(동점은 같은 순위, 1부터). */
data class RankedFactorScore(
    val score: RegionScore,
    val rank: Int,
)
