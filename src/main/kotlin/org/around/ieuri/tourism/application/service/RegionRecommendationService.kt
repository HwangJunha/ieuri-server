package org.around.ieuri.tourism.application.service

import org.around.ieuri.tourism.application.policy.RegionRecommendationPolicy
import org.around.ieuri.tourism.application.port.`in`.query.RegionRecommendationUseCase
import org.around.ieuri.tourism.application.port.out.query.RegionMetricQueryPort
import org.around.ieuri.tourism.application.query.RecommendedRegionItemView
import org.around.ieuri.tourism.application.query.RecommendedRegionQuery
import org.around.ieuri.tourism.application.query.RecommendedRegionView
import org.around.ieuri.tourism.application.query.RegionMetricRow
import org.around.ieuri.tourism.domain.strategy.RegionScoreCalculator
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.RegionScore
import org.around.ieuri.tourism.domain.vo.RegionScoreFactor
import org.around.ieuri.tourism.domain.vo.ScoreNormalization
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 지역 추천 조회 유스케이스 구현(SPEC-0015, CQRS 읽기).
 *
 * 순서가 곧 설계다: 원본 적재 → **적재된 집합에서 정규화 구간 산출**(§3.3) → 거리 항목 가산
 * (origin이 있을 때만, R21) → 가중합(R30 미만은 탈락) → 총점 내림차순 정렬(R16) → 개수 제한.
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
        val rows = regionMetricQueryPort.findLatestMetrics()
        val dataBasisAt = resolveDataBasisAt(rows)
        // 적재된 데이터가 하나도 없으면 기준 시점이 null이고 결과도 비어 있다(R20).
        if (dataBasisAt == null) {
            return RecommendedRegionView(regions = emptyList(), dataBasisAt = null)
        }

        val normalizations = buildNormalizations(rows)
        val calculator = RegionScoreCalculator(policy.weights, policy.minFactorCount)

        val regions = rows.mapNotNull { row ->
            val factorScores = row.normalizedScores(normalizations) + row.distanceScore(query.origin)
            // 항목 수가 최소 개수 미만이면 null — 추천 대상이 아니다(R30).
            val totalScore = calculator.calculate(factorScores) ?: return@mapNotNull null

            RecommendedRegionItemView(
                code = row.code,
                name = row.name.resolve(locale),
                provinceName = row.provinceName.resolve(locale),
                totalScore = totalScore.total,
                scores = totalScore.factorScores,
                scoreFactorCount = totalScore.scoreFactorCount,
                center = row.center,
            )
        }
            // **총점 단일 기준이다**(R16). 항목 수를 정렬에 섞으면 "덜 알려진 지역이 무조건
            // 밀린다"는 다른 편향이 생긴다. 동점은 코드 오름차순으로 안정화한다.
            .sortedWith(compareByDescending<RecommendedRegionItemView> { it.totalScore }.thenBy { it.code })
            .take(query.limit)

        // 여기서 regions가 비어 있어도 dataBasisAt은 채워진 채로 나간다 — "데이터는 있으나
        // 조건을 만족한 지역이 없음"과 "아무것도 적재되지 않음"은 다른 상태다(R20).
        return RecommendedRegionView(regions = regions, dataBasisAt = dataBasisAt)
    }

    /**
     * 실제로 **쓰인** 데이터의 기준 시점(R20). 값이 하나도 없으면 null이다.
     *
     * **가장 최근이 아니라 가장 오래된 기준일이다.** 지표마다 신선도가 크게 다르다(실측:
     * 집중률은 오늘, 방문자수는 30일 전, 월 지표는 2개월 전). 최신값을 표기하면 "오늘 측정한
     * 데이터"로 읽혀 실시간이라는 오해를 준다 — R24가 금지하는 바로 그 표기다. 가장 오래된
     * 것을 쓰면 "적어도 이 시점까지의 데이터"라는 보수적이고 참인 진술이 된다.
     *
     * 월 단위 지표는 해당 월 1일로 정규화돼 있어 일 단위와 같은 축에서 비교된다(V44).
     */
    private fun resolveDataBasisAt(rows: List<RegionMetricRow>): LocalDateTime? =
        rows.flatMap(RegionMetricRow::basisDates).minOrNull()?.atStartOfDay()

    /**
     * 항목별 정규화 구간을 **적재된 집합에서** 뽑는다(§3.3).
     *
     * 값이 있는 지역이 없는 항목은 구간 자체가 없어 그 항목이 전 지역에서 빠진다 — 0으로
     * 채우지 않는다(POL-043).
     */
    private fun buildNormalizations(
        rows: List<RegionMetricRow>,
    ): Map<RegionScoreFactor, ScoreNormalization> = buildMap {
        for ((factor, extractor) in RAW_EXTRACTORS) {
            val values = rows.mapNotNull(extractor)
            if (values.isEmpty()) continue
            put(factor, ScoreNormalization(min = values.min(), max = values.max()))
        }
    }

    /** 값이 있고 구간도 있는 항목만 점수가 된다. 결측은 목록에서 빠진다(R14). */
    private fun RegionMetricRow.normalizedScores(
        normalizations: Map<RegionScoreFactor, ScoreNormalization>,
    ): List<RegionScore> = RAW_EXTRACTORS.mapNotNull { (factor, extractor) ->
        val raw = extractor(this) ?: return@mapNotNull null
        normalizations[factor]?.normalize(factor, raw)
    }

    /** 거리 항목은 origin이 있을 때만 붙는다(R21). 부재는 예외가 아니라 빈 목록이다. */
    private fun RegionMetricRow.distanceScore(origin: Coordinates?): List<RegionScore> =
        origin?.let { listOf(policy.distanceScoring.score(it, center)) } ?: emptyList()

    private companion object {
        /**
         * 항목 → 원본 값 추출. 정규화 구간 산출과 점수 산출이 **같은 목록을 보게** 묶어 둔다 —
         * 한쪽에만 항목을 추가하면 구간 없는 항목이 조용히 사라진다.
         *
         * 거리는 여기 없다 — 적재된 값이 아니라 요청 시점에 계산되기 때문이다.
         */
        val RAW_EXTRACTORS: List<Pair<RegionScoreFactor, (RegionMetricRow) -> Double?>> = listOf(
            RegionScoreFactor.CONGESTION to RegionMetricRow::concentrationRate,
            RegionScoreFactor.VISITOR_VOLUME to RegionMetricRow::visitorCount,
            RegionScoreFactor.STAY_INTENSITY to RegionMetricRow::stayIntensity,
            RegionScoreFactor.SPENDING_INTENSITY to RegionMetricRow::spendingIntensity,
        )
    }
}
