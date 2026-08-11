package org.around.ieuri.tourism.application.service

import org.around.ieuri.tourism.application.port.out.query.RegionMetricQueryPort
import org.around.ieuri.tourism.application.query.OriginDistrict
import org.around.ieuri.tourism.application.query.RegionMetric
import org.around.ieuri.tourism.application.query.RegionRow
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.RegionScoreFactor

/**
 * 지표 질의 포트 스텁 — 지역 1건과 그 지역의 항목들을 함께 적어 두고 5개 질의로 흩뿌린다.
 *
 * 포트가 지표별로 나뉘어 있어 테스트마다 맵 4개를 손으로 짜면 지역 코드가 어긋나기 쉽다.
 * 여기서 **코드를 키로 뒤집어** 결합 규칙(코드가 맞는 것만 붙는다)이 실제로 걸리게 한다.
 *
 * **다섯 질의가 전부 시도로 거른다** — 실제 SQL의 `province_code = ?`를 여기서도 재현해야
 * 시도 필터가 서비스에서 사라진 것이 테스트에 잡힌다.
 *
 * [originDistrict]는 PostGIS 경계 판정을 대신한다. 기본 null이라 좌표를 줘도 어느 시군구에도
 * 걸리지 않는 상태(바다·해외)가 기본값이다.
 *
 * [askedProvinceCodes]는 실제로 좁혀 읽었는지 확인하는 데 쓴다 — 전국을 읽고 나중에 버리는
 * 형태로 되돌아가면 여기에 드러난다.
 */
class RegionMetricQueryPortStub(
    private val fixtures: List<RegionFixture>,
    private val originDistrict: OriginDistrict? = null,
) : RegionMetricQueryPort {

    val askedProvinceCodes: MutableList<String> = mutableListOf()

    override fun findDistrictAt(origin: Coordinates): OriginDistrict? = originDistrict

    override fun findRegions(provinceCode: String): List<RegionRow> =
        inProvince(provinceCode).map { it.region }

    override fun findConcentrationRates(provinceCode: String) =
        metricsOf(RegionScoreFactor.CONGESTION, provinceCode)

    override fun findVisitorCounts(provinceCode: String) =
        metricsOf(RegionScoreFactor.VISITOR_VOLUME, provinceCode)

    override fun findStayIntensities(provinceCode: String) =
        metricsOf(RegionScoreFactor.STAY_INTENSITY, provinceCode)

    override fun findSpendingIntensities(provinceCode: String) =
        metricsOf(RegionScoreFactor.SPENDING_INTENSITY, provinceCode)

    /** 그 항목에 값이 있는 지역만 들어간다 — 결측은 키 자체가 없다(POL-043). */
    private fun metricsOf(factor: RegionScoreFactor, provinceCode: String): Map<String, RegionMetric> =
        inProvince(provinceCode).mapNotNull { fixture ->
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
