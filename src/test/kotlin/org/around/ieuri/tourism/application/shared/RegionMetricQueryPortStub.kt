package org.around.ieuri.tourism.application.shared

import org.around.ieuri.tourism.application.shared.provided.dto.OriginDistrict
import org.around.ieuri.tourism.application.shared.provided.dto.RegionMetric
import org.around.ieuri.tourism.application.shared.provided.dto.RegionRow
import org.around.ieuri.tourism.application.shared.required.RegionMetricQueryPort
import org.around.ieuri.tourism.domain.shared.Coordinates
import org.around.ieuri.tourism.domain.shared.RegionScoreFactor

/**
 * 지표 질의 포트 스텁 — 지역 1건과 그 지역의 항목들을 함께 적어 두고 질의별로 흩뿌린다.
 * - 모든 질의가 실제 SQL처럼 시도로 거른다.
 * - [originDistrict]는 경계 판정을 대신하며 기본 null(어느 시군구에도 걸리지 않음)이다.
 * - [askedProvinceCodes]로 실제로 좁혀 읽었는지 확인한다.
 */
class RegionMetricQueryPortStub(
    private val fixtures: List<RegionFixture>,
    private val originDistrict: OriginDistrict? = null,
) : RegionMetricQueryPort {

    val askedProvinceCodes: MutableList<String> = mutableListOf()

    override fun findDistrictAt(origin: Coordinates): OriginDistrict? = originDistrict

    override fun findRegions(provinceCode: String): List<RegionRow> = inProvince(provinceCode).map { it.region }

    override fun findConcentrationRates(provinceCode: String) = metricsOf(RegionScoreFactor.CONGESTION, provinceCode)

    override fun findVisitorCounts(provinceCode: String) = metricsOf(RegionScoreFactor.VISITOR_VOLUME, provinceCode)

    override fun findStayIntensities(provinceCode: String) = metricsOf(RegionScoreFactor.STAY_INTENSITY, provinceCode)

    override fun findSpendingIntensities(provinceCode: String) = metricsOf(RegionScoreFactor.SPENDING_INTENSITY, provinceCode)

    /** 그 항목에 값이 있는 지역만 들어간다 — 결측은 키 자체가 없다(POL-043). */
    private fun metricsOf(factor: RegionScoreFactor, provinceCode: String): Map<String, RegionMetric> = inProvince(provinceCode).mapNotNull { fixture ->
        fixture.metrics[factor]?.let { fixture.region.code to it }
    }.toMap()

    private fun inProvince(provinceCode: String): List<RegionFixture> {
        askedProvinceCodes += provinceCode
        return fixtures.filter { it.region.provinceCode == provinceCode }
    }
}

/** 한 지역과 그 지역에서 값이 있었던 항목들. */
data class RegionFixture(
    val region: RegionRow,
    val metrics: Map<RegionScoreFactor, RegionMetric>,
)
