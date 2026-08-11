package org.around.ieuri.tourism.application.service

import org.around.ieuri.tourism.application.policy.RegionRecommendationPolicy
import org.around.ieuri.tourism.application.port.`in`.query.RegionRecommendationUseCase
import org.around.ieuri.tourism.application.port.out.query.RegionMetricQueryPort
import org.around.ieuri.tourism.application.query.RecommendedRegionItemView
import org.around.ieuri.tourism.application.query.RecommendedRegionQuery
import org.around.ieuri.tourism.application.query.RecommendedRegionView
import org.around.ieuri.tourism.application.query.RegionMetricRow
import org.around.ieuri.tourism.application.query.RegionScoreView
import org.around.ieuri.tourism.domain.strategy.RegionScoreCalculator
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.RegionScoreFactor
import org.around.ieuri.tourism.domain.vo.ScoreNormalization
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * 지역 추천 조회 유스케이스 구현(SPEC-0015, CQRS 읽기).
 *
 * 순서가 곧 설계다: 원본 적재 → **적재된 집합에서 정규화 구간 산출**(§3.3) → 신선도 감쇠 →
 * 거리 항목 가산(origin이 있을 때만, R21) → 가중합(R30 미만은 탈락) → 총점 내림차순 정렬
 * (R16) → 개수 제한.
 *
 * 정규화 구간을 전체 집합에서 뽑기 때문에 **한 지역만 따로 계산할 수 없다** — 척도가 이웃
 * 지역들에 의존한다. 상한을 먼저 적용하면 척도가 바뀌므로 자르기는 마지막이다.
 */
@Service
class RegionRecommendationService(
    private val regionMetricQueryPort: RegionMetricQueryPort,
    private val policy: RegionRecommendationPolicy,
) : RegionRecommendationUseCase {

    override fun recommendedRegions(
        query: RecommendedRegionQuery,
        locale: DomainLocale,
    ): RecommendedRegionView {
        val rows = loadMetricRows()

        val normalizations = buildNormalizations(rows)
        val calculator = RegionScoreCalculator(policy.weights, policy.minFactorCount)
        val regions = rows.mapNotNull { row ->
            val scoreViews = row.normalizedScores(normalizations) + row.distanceScore(query.origin)
            // 항목 수가 최소 개수 미만이면 null — 추천 대상이 아니다(R30).
            val totalScore = calculator.calculate(scoreViews.map { it.score }) ?: return@mapNotNull null

            RecommendedRegionItemView(
                code = row.code,
                name = row.name.resolve(locale),
                provinceName = row.provinceName.resolve(locale),
                totalScore = totalScore.total,
                scores = scoreViews,
                scoreFactorCount = totalScore.scoreFactorCount,
                center = row.center,
            )
        }
            // **총점 단일 기준이다**(R16). 항목 수를 정렬에 섞으면 "덜 알려진 지역이 무조건
            // 밀린다"는 다른 편향이 생긴다. 동점은 코드 오름차순으로 안정화한다.
            .sortedWith(compareByDescending<RecommendedRegionItemView> { it.totalScore }.thenBy { it.code })
            .take(query.limit)

        // 적재된 데이터가 없으면 전 지역이 최소 항목 수(R30)에 걸려 빈 목록이 된다 —
        // 별도 분기가 필요 없다. 기준 시점은 항목별로 각 점수에 실린다(R20).
        return RecommendedRegionView(regions = regions)
    }


    /**
     * 지역 목록과 지표 4종을 각각 읽어 **코드로 합친다**.
     *
     * 지표마다 집계 단위가 달라 질의를 나눴다(포트 참조). 결합 규칙이 SQL이 아니라 여기
     * 있으므로, 지표가 늘거나 규칙이 바뀌어도 읽을 곳은 이 함수 하나다.
     *
     * 값이 없는 지표는 맵에 키가 없어 그대로 빠진다 — 0으로 채우지 않는다(POL-043).
     */
    private fun loadMetricRows(): List<RegionMetricRow> {
        val regions = regionMetricQueryPort.findRegions()
        val byFactor = mapOf(
            RegionScoreFactor.CONGESTION to regionMetricQueryPort.findConcentrationRates(),
            RegionScoreFactor.VISITOR_VOLUME to regionMetricQueryPort.findVisitorCounts(),
            RegionScoreFactor.STAY_INTENSITY to regionMetricQueryPort.findStayIntensities(),
            RegionScoreFactor.SPENDING_INTENSITY to regionMetricQueryPort.findSpendingIntensities(),
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

    /**
     * 항목별 정규화 구간을 **적재된 집합에서** 뽑는다(§3.3).
     *
     * 값이 있는 지역이 없는 항목은 구간 자체가 없어 그 항목이 전 지역에서 빠진다 — 0으로
     * 채우지 않는다(POL-043).
     */
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
     * 값이 있고 구간도 있는 항목만 점수가 된다. 결측은 목록에서 빠진다(R14).
     *
     * **신선도 감쇠는 방문자수에만 건다**([DECAYING_FACTORS]). 나머지 셋은 성격상 나이가
     * 다르게 취급된다 — 집중률은 애초에 오늘 것만 쓰고, 월 지표는 원래 월 단위라 수십 일
     * 지난 것이 정상이다. 감쇠는 정규화된 **점수**에 곱한다: 가중치를 깎으면 분모도 함께
     * 줄어 총점이 그대로다.
     */
    private fun RegionMetricRow.normalizedScores(
        normalizations: Map<RegionScoreFactor, ScoreNormalization>,
    ): List<RegionScoreView> = metrics.entries.mapNotNull { (factor, metric) ->
        val normalization = normalizations[factor] ?: return@mapNotNull null
        val score = normalization.normalize(factor, metric.rawValue)
        // 나이는 DB가 한국 날짜로 계산해 넘긴다 — 앱이 `오늘`을 따로 정하지 않는다.
        val age = metric.ageDays
        val decayed = if (factor in DECAYING_FACTORS && age != null) {
            policy.freshnessDecay.apply(score, age)
        } else {
            score
        }
        RegionScoreView(score = decayed, basisAt = metric.basisDate)
    }

    /**
     * 거리 항목은 origin이 있을 때만 붙는다(R21). 부재는 예외가 아니라 빈 목록이다.
     *
     * 기준일이 없다 — 적재된 값이 아니라 요청 시점에 계산되기 때문이다.
     */
    private fun RegionMetricRow.distanceScore(origin: Coordinates?): List<RegionScoreView> =
        origin?.let { listOf(RegionScoreView(policy.distanceScoring.score(it, center), basisAt = null)) }
            ?: emptyList()

    private companion object {
        /**
         * 신선도 감쇠를 적용하는 항목.
         *
         * 방문자수만 넣는다 — 적재 지연이 실제로 관측된 유일한 항목이다(실측: 최신 기준일이
         * 약 30일 전). 다른 항목에 같은 감쇠를 걸면 월 지표가 정상 주기만으로 반값이 된다.
         */
        val DECAYING_FACTORS = setOf(RegionScoreFactor.VISITOR_VOLUME)
    }
}
