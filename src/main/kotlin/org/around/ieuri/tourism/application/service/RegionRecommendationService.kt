package org.around.ieuri.tourism.application.service

import org.around.ieuri.tourism.application.policy.RegionRecommendationPolicy
import org.around.ieuri.tourism.application.port.`in`.query.RegionRecommendationUseCase
import org.around.ieuri.tourism.application.port.out.query.RegionMetricQueryPort
import org.around.ieuri.tourism.application.query.OriginDistrict
import org.around.ieuri.tourism.application.query.RecommendedRegionItemView
import org.around.ieuri.tourism.application.query.RecommendedRegionQuery
import org.around.ieuri.tourism.application.query.RecommendedRegionView
import org.around.ieuri.tourism.application.query.RegionMetricRow
import org.around.ieuri.tourism.application.query.RegionScoreView
import org.around.ieuri.tourism.application.query.ScoredFactor
import org.around.ieuri.tourism.domain.strategy.RegionScoreCalculator
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.RegionScore
import org.around.ieuri.tourism.domain.vo.RegionScoreFactor
import org.around.ieuri.tourism.domain.vo.RegionTotalScore
import org.around.ieuri.tourism.domain.vo.ScoreNormalization
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 지역 추천 조회 유스케이스 구현(SPEC-0015).
 * - 정규화 구간과 순위는 후보 집합 전체에서 나오므로, 개수 제한은 반드시 마지막에 적용한다.
 */
@Service
@Transactional(readOnly = true)
class RegionRecommendationService(
    private val regionMetricQueryPort: RegionMetricQueryPort,
    private val policy: RegionRecommendationPolicy,
) : RegionRecommendationUseCase {

    override fun recommendedRegions(
        query: RecommendedRegionQuery,
        locale: DomainLocale,
    ): RecommendedRegionView {
        // 어느 시군구에도 걸리지 않는 좌표(바다·해외)는 빈 목록이다 — 오류도, 전국 폴백도 아니다.
        val originDistrict = regionMetricQueryPort.findDistrictAt(query.origin)
            ?: return RecommendedRegionView(regions = emptyList())

        val loaded = loadMetricRows(originDistrict.provinceCode)
        // 자기 지역을 포함한 시도 전체를 센다.
        if (loaded.size < policy.minDistrictsPerProvince) {
            return RecommendedRegionView(regions = emptyList())
        }
        val rows = selectCandidates(loaded, originDistrict)

        val normalizations = buildNormalizations(rows)
        val calculator = RegionScoreCalculator(policy.weights, policy.minFactorCount)
        val scored = rows.mapNotNull { row ->
            val factors = row.normalizedScores(normalizations) + row.distanceScore(query.origin)
            // 항목 수가 최소 개수 미만이면 null — 추천 대상이 아니다(R30).
            val totalScore = calculator.calculate(factors.map { it.score }) ?: return@mapNotNull null
            ScoredRegion(row, factors, totalScore)
        }

        // 순위는 take(limit) 전에 매긴다 — 반환 건수와 무관해야 한다.
        val ranks = rankByFactor(scored)
        val regions = scored
            // 총점 내림차순, 동점은 코드 오름차순.
            .sortedWith(compareByDescending<ScoredRegion> { it.totalScore.total }.thenBy { it.code })
            .take(query.limit)
            .map { it.toView(locale, ranks) }

        return RecommendedRegionView(regions = regions)
    }

    /**
     * 항목별 순위를 후보 전체에서 [RegionScore.value] 내림차순으로 매긴다.
     * - 동점은 같은 순위를 받고 다음 순위를 건너뛴다(1, 2, 2, 4). 모수는 그 항목이 산출된 지역뿐이다.
     */
    private fun rankByFactor(scored: List<ScoredRegion>): Map<FactorRankKey, Int> = buildMap {
        val byFactor = scored
            .flatMap { region -> region.factors.map { region.code to it.score } }
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
                    put(FactorRankKey(code, factor), rank)
                }
        }
    }

    private fun ScoredRegion.toView(
        locale: DomainLocale,
        ranks: Map<FactorRankKey, Int>,
    ) = RecommendedRegionItemView(
        code = code,
        name = row.name.resolve(locale),
        provinceName = row.provinceName.resolve(locale),
        scores = factors.map { factor ->
            RegionScoreView(
                score = factor.score,
                // 점수를 만든 그 집합에서 매긴 순위라 키가 반드시 있다.
                rank = ranks.getValue(FactorRankKey(code, factor.score.factor)),
            )
        },
        scoreFactorCount = totalScore.scoreFactorCount,
        center = row.center,
    )

    /** 추천 후보 선별 — 자기 지역만 뺀다. */
    private fun selectCandidates(
        rows: List<RegionMetricRow>,
        originDistrict: OriginDistrict,
    ): List<RegionMetricRow> = rows.filter { it.code != originDistrict.code }

    /** 한 시도의 지역 목록과 지표 4종을 코드로 합친다. 값이 없는 지표는 0으로 채우지 않고 뺀다(POL-043). */
    private fun loadMetricRows(provinceCode: String): List<RegionMetricRow> {
        val regions = regionMetricQueryPort.findRegions(provinceCode)
        val byFactor = mapOf(
            RegionScoreFactor.CONGESTION to regionMetricQueryPort.findConcentrationRates(provinceCode),
            RegionScoreFactor.VISITOR_VOLUME to regionMetricQueryPort.findVisitorCounts(provinceCode),
            RegionScoreFactor.STAY_INTENSITY to regionMetricQueryPort.findStayIntensities(provinceCode),
            RegionScoreFactor.SPENDING_INTENSITY to regionMetricQueryPort.findSpendingIntensities(provinceCode),
        )

        return regions.map { region ->
            RegionMetricRow(
                region = region,
                metrics = buildMap {
                    for ((factor, metrics) in byFactor) {
                        metrics[region.code]?.let { put(factor, it) }
                    }
                },
            )
        }
    }

    /** 항목별 정규화 구간을 적재된 집합에서 뽑는다. 값이 하나도 없는 항목은 전 지역에서 빠진다. */
    private fun buildNormalizations(
        rows: List<RegionMetricRow>,
    ): Map<RegionScoreFactor, ScoreNormalization> = buildMap {
        val byFactor = rows.flatMap { it.metrics.entries }.groupBy({ it.key }, { it.value.rawValue })
        for ((factor, values) in byFactor) {
            if (values.isEmpty()) continue
            put(factor, ScoreNormalization(min = values.min(), max = values.max()))
        }
    }

    /**
     * 값과 구간이 모두 있는 항목만 점수가 된다.
     * - 신선도 감쇠는 [DECAYING_FACTORS]에만, 가중치가 아니라 정규화된 점수에 곱한다.
     */
    private fun RegionMetricRow.normalizedScores(
        normalizations: Map<RegionScoreFactor, ScoreNormalization>,
    ): List<ScoredFactor> = metrics.entries.mapNotNull { (factor, metric) ->
        val normalization = normalizations[factor] ?: return@mapNotNull null
        val score = normalization.normalize(factor, metric.rawValue)
        val age = metric.ageDays
        val decayed = if (factor in DECAYING_FACTORS && age != null) {
            policy.freshnessDecay.apply(score, age)
        } else {
            score
        }
        ScoredFactor(score = decayed)
    }

    /** 거리 항목은 origin이 필수라 항상 붙는다(R21). */
    private fun RegionMetricRow.distanceScore(origin: Coordinates): ScoredFactor = ScoredFactor(policy.distanceScoring.score(origin, center))

    /** 총점까지 산출된 후보 1건. [totalScore]는 정렬에만 쓰고 응답에는 나가지 않는다. */
    private data class ScoredRegion(
        val row: RegionMetricRow,
        val factors: List<ScoredFactor>,
        val totalScore: RegionTotalScore,
    ) {
        val code: String get() = row.code
    }

    /** 순위 조회 키 — (지역, 항목) 짝. */
    private data class FactorRankKey(
        val code: String,
        val factor: RegionScoreFactor,
    )

    private companion object {
        /** 신선도 감쇠를 적용하는 항목. */
        val DECAYING_FACTORS = setOf(RegionScoreFactor.VISITOR_VOLUME)
    }
}
