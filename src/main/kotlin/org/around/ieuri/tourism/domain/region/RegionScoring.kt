package org.around.ieuri.tourism.domain.region

import org.around.ieuri.tourism.domain.shared.Coordinates

/**
 * 지역 추천 점수 모델(SPEC-0015). 같은 시도의 후보들에 항목 점수·총점·항목별 순위를 매겨 총점순으로 돌려준다.
 * - 항목 점수: 후보 집합의 최소~최대로 정규화(혼잡도는 뒤집음). 방문자 수만 신선도 감쇠. 거리는 origin에서 계산해 항상 붙는다(R21).
 * - 총점: 존재하는 항목의 가중 평균. 항목 수가 [minFactorCount] 미만이거나 가중치 합이 0이면 후보에서 빠진다(R30).
 * - 순서: 총점 내림차순, 동점은 코드 오름차순. 순위는 동점 같은 순위(1, 2, 2, 4)이고 모수는 그 항목이 산출된 후보다.
 */
class RegionScoring(
    private val weights: RegionScoreWeights,
    private val distanceScoring: DistanceScoring,
    private val freshnessDecay: FreshnessDecay,
    private val minFactorCount: Int,
) {
    init {
        require(minFactorCount >= 1) { "최소 항목 수는 1 이상이어야 합니다: $minFactorCount" }
    }

    fun rank(candidates: List<RegionCandidate>, origin: Coordinates): List<ScoredRegion> {
        val normalizations = normalizationsOf(candidates)
        val scored = candidates.mapNotNull { candidate ->
            val factors = candidate.normalizedScores(normalizations) + distanceScoring.score(origin, candidate.center)
            val total = total(factors) ?: return@mapNotNull null
            Triple(candidate.code, factors, total)
        }
        // 순위는 정렬·자르기 전에 매긴다 — 반환 건수와 무관해야 한다.
        val ranks = ranksByFactor(scored.map { (code, factors, _) -> code to factors })
        return scored
            .sortedWith(compareByDescending<Triple<String, List<RegionScore>, RegionTotalScore>> { it.third.total }.thenBy { it.first })
            .map { (code, factors, total) ->
                ScoredRegion(code, factors.map { RankedFactorScore(it, ranks.getValue(code to it.factor)) }, total)
            }
    }

    /** 항목별 정규화 구간을 후보 집합에서 뽑는다. 값이 하나도 없는 항목은 전 지역에서 빠진다. */
    private fun normalizationsOf(candidates: List<RegionCandidate>): Map<RegionScoreFactor, ScoreNormalization> = buildMap {
        val byFactor = candidates.flatMap { it.metrics.entries }.groupBy({ it.key }, { it.value.rawValue })
        for ((factor, values) in byFactor) {
            if (values.isEmpty()) continue
            put(factor, ScoreNormalization(min = values.min(), max = values.max()))
        }
    }

    /** 값과 구간이 모두 있는 항목만 점수가 된다. 감쇠는 가중치가 아니라 정규화된 점수에 곱한다. */
    private fun RegionCandidate.normalizedScores(normalizations: Map<RegionScoreFactor, ScoreNormalization>): List<RegionScore> = metrics.entries.mapNotNull { (factor, metric) ->
        val normalization = normalizations[factor] ?: return@mapNotNull null
        val score = normalization.normalize(factor, metric.rawValue)
        val age = metric.ageDays
        if (factor in DECAYING_FACTORS && age != null) freshnessDecay.apply(score, age) else score
    }

    private fun total(factorScores: List<RegionScore>): RegionTotalScore? {
        val present = factorScores.distinctBy { it.factor }
        if (present.size < minFactorCount) return null

        val weightSum = present.sumOf { weights[it.factor] }
        if (weightSum <= 0.0) return null

        val weighted = present.sumOf { it.value * weights[it.factor] }
        val total = (weighted / weightSum).coerceIn(RegionScore.MIN_VALUE, RegionScore.MAX_VALUE)
        return RegionTotalScore(total, present)
    }

    private fun ranksByFactor(scored: List<Pair<String, List<RegionScore>>>): Map<Pair<String, RegionScoreFactor>, Int> = buildMap {
        val byFactor = scored
            .flatMap { (code, factors) -> factors.map { code to it } }
            .groupBy({ (_, score) -> score.factor }, { (code, score) -> code to score.value })

        for ((factor, entries) in byFactor) {
            var rank = 0
            var previousValue: Double? = null
            entries.sortedByDescending { (_, value) -> value }
                .forEachIndexed { index, (code, value) ->
                    // 동점이면 앞 순위를 그대로 쓰고, 아니면 지금까지 본 개수가 곧 순위다.
                    if (value != previousValue) {
                        rank = index + 1
                        previousValue = value
                    }
                    put(code to factor, rank)
                }
        }
    }

    private companion object {
        /** 신선도 감쇠를 적용하는 항목. */
        val DECAYING_FACTORS = setOf(RegionScoreFactor.VISITOR_VOLUME)
    }
}
