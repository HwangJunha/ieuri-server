package org.around.ieuri.tourism.application.region

import org.around.ieuri.common.event.LocationAccessed
import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.tourism.application.region.provided.RegionRecommendationUseCase
import org.around.ieuri.tourism.application.region.provided.dto.RecommendedRegionItemView
import org.around.ieuri.tourism.application.region.provided.dto.RecommendedRegionQuery
import org.around.ieuri.tourism.application.region.provided.dto.RecommendedRegionView
import org.around.ieuri.tourism.application.region.provided.dto.RegionScoreView
import org.around.ieuri.tourism.application.region.required.RegionEventPublisherPort
import org.around.ieuri.tourism.application.region.required.RegionMetricQueryPort
import org.around.ieuri.tourism.application.region.required.dto.RegionRow
import org.around.ieuri.tourism.domain.region.RegionCandidate
import org.around.ieuri.tourism.domain.region.RegionScoreFactor
import org.around.ieuri.tourism.domain.region.ScoredRegion
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 지역 추천 조회 유스케이스 구현(SPEC-0015). 점수·순위·정렬은 [org.around.ieuri.tourism.domain.region.RegionScoring]이 한다.
 * - 정규화 구간과 순위는 후보 집합 전체에서 나오므로, 개수 제한은 반드시 마지막에 적용한다.
 */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX, readOnly = true)
class RegionRecommendationService(
    private val regionMetricQueryPort: RegionMetricQueryPort,
    private val policy: RegionRecommendationPolicy,
    private val eventPublisher: RegionEventPublisherPort,
) : RegionRecommendationUseCase {

    override fun recommendedRegions(
        query: RecommendedRegionQuery,
        locale: DomainLocale,
        memberId: Long?,
    ): RecommendedRegionView {
        // 좌표 조회 사실을 남긴다(SPEC-0032 R19). origin은 넘기지 않고, 비로그인은 발행하지 않는다.
        memberId?.let { eventPublisher.publish(LocationAccessed(it, "recommendedRegions")) }
        // 어느 시군구에도 걸리지 않는 좌표(바다·해외)는 빈 목록이다 — 오류도, 전국 폴백도 아니다.
        val originDistrict = regionMetricQueryPort.findDistrictAt(query.origin)
            ?: return RecommendedRegionView(regions = emptyList())

        val regions = regionMetricQueryPort.findRegions(originDistrict.provinceCode)
        // 자기 지역을 포함한 시도 전체를 센다.
        if (!policy.provinceEligibility.isSatisfiedBy(regions.size)) {
            return RecommendedRegionView(regions = emptyList())
        }
        // 추천 후보는 자기 지역만 뺀다.
        val candidates = candidatesOf(regions.filter { it.code != originDistrict.code }, originDistrict.provinceCode)
        val byCode = regions.associateBy { it.code }

        val items = policy.scoring.rank(candidates, query.origin)
            .take(query.limit)
            .map { it.toView(byCode.getValue(it.code), locale) }
        return RecommendedRegionView(regions = items)
    }

    /** 지역 목록과 지표 4종을 코드로 합친다. 값이 없는 지표는 0으로 채우지 않고 뺀다(POL-043). */
    private fun candidatesOf(regions: List<RegionRow>, provinceCode: String): List<RegionCandidate> {
        val byFactor = mapOf(
            RegionScoreFactor.CONGESTION to regionMetricQueryPort.findConcentrationRates(provinceCode),
            RegionScoreFactor.VISITOR_VOLUME to regionMetricQueryPort.findVisitorCounts(provinceCode),
            RegionScoreFactor.STAY_INTENSITY to regionMetricQueryPort.findStayIntensities(provinceCode),
            RegionScoreFactor.SPENDING_INTENSITY to regionMetricQueryPort.findSpendingIntensities(provinceCode),
        )
        return regions.map { region ->
            RegionCandidate(
                code = region.code,
                center = region.center,
                metrics = buildMap {
                    for ((factor, metrics) in byFactor) {
                        metrics[region.code]?.let { put(factor, it) }
                    }
                },
            )
        }
    }

    private fun ScoredRegion.toView(region: RegionRow, locale: DomainLocale) = RecommendedRegionItemView(
        code = code,
        name = region.name.resolve(locale),
        provinceName = region.provinceName.resolve(locale),
        scores = factors.map { RegionScoreView(score = it.score, rank = it.rank) },
        scoreFactorCount = total.scoreFactorCount,
        center = region.center,
    )
}
