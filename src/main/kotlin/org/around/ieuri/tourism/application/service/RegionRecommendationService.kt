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
import org.around.ieuri.tourism.domain.strategy.RegionScoreCalculator
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.RegionScoreFactor
import org.around.ieuri.tourism.domain.vo.ScoreNormalization
import org.springframework.stereotype.Service

/**
 * 지역 추천 조회 유스케이스 구현(SPEC-0015, CQRS 읽기).
 *
 * 순서가 곧 설계다: **origin의 시도 판정** → 그 시도만 적재 → 후보 선별([selectCandidates])
 * → **남은 후보 집합에서 정규화 구간 산출**(§3.3) → 신선도 감쇠 → 거리 항목 가산 → 가중합
 * (R30 미만은 탈락) → 총점 내림차순 정렬(R16) → 개수 제한.
 *
 * **시도 판정이 맨 앞이다.** 추천 대상이 origin의 시도로 한정되므로(R21) 그 밖은 읽지 않는다.
 * 척도도 실제로 비교 가능한 집합에서 나와야 한다 — 전국에서 뽑은 구간을 한 시도에 적용하면
 * 그 시도 안의 차이가 눌린다.
 *
 * 정규화 구간을 집합 전체에서 뽑기 때문에 **한 지역만 따로 계산할 수 없다** — 척도가 이웃
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
        // 어느 시군구에도 걸리지 않는 좌표(바다·해외)는 빈 목록이다 — 오류도, 전국 폴백도
        // 아니다. 좁힐 시도를 모르면 "근처의 다른 곳"이라는 질문 자체가 성립하지 않는다.
        val originDistrict = regionMetricQueryPort.findDistrictAt(query.origin)
            ?: return RecommendedRegionView(regions = emptyList())

        val loaded = loadMetricRows(originDistrict.provinceCode)
        // 선택지가 [RegionRecommendationPolicy.minDistrictsPerProvince] 미만인 시도(세종·제주)
        // 에서는 추천이 성립하지 않는다. 자기 지역을 뺀 나머지가 아니라 시도 전체를 센다 —
        // 기준은 "이 시도가 고를 만한 곳인가"다.
        if (loaded.size < policy.minDistrictsPerProvince) {
            return RecommendedRegionView(regions = emptyList())
        }
        val rows = selectCandidates(loaded, originDistrict)

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
     * 추천 후보 선별 — 자기 지역만 뺀다. 추천은 "여기 말고 근처 저기"라 origin이 들어앉은
     * 시군구는 추천이 아니라 현재 위치 확인이다.
     *
     * 같은 시도로 좁히는 일과 소규모 시도 제외는 여기 없다. 앞의 둘은 각각 질의 조건과
     * 호출부 가드로 옮겼다 — 226건을 읽어 202건을 버리던 자리다.
     */
    private fun selectCandidates(
        rows: List<RegionMetricRow>,
        originDistrict: OriginDistrict,
    ): List<RegionMetricRow> = rows.filter { it.code != originDistrict.code }

    /**
     * 한 시도의 지역 목록과 지표 4종을 각각 읽어 **코드로 합친다**.
     *
     * 지표마다 집계 단위가 달라 질의를 나눴다(포트 참조). 결합 규칙이 SQL이 아니라 여기
     * 있으므로, 지표가 늘거나 규칙이 바뀌어도 읽을 곳은 이 함수 하나다.
     *
     * 값이 없는 지표는 맵에 키가 없어 그대로 빠진다 — 0으로 채우지 않는다(POL-043).
     */
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
     * 거리 항목은 origin이 필수라 항상 붙는다(R21).
     *
     * 기준일이 없다 — 적재된 값이 아니라 요청 시점에 계산되기 때문이다.
     */
    private fun RegionMetricRow.distanceScore(origin: Coordinates): RegionScoreView =
        RegionScoreView(policy.distanceScoring.score(origin, center), basisAt = null)

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
