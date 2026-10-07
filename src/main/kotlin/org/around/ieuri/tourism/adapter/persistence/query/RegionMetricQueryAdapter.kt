package org.around.ieuri.tourism.adapter.persistence.query

import org.around.ieuri.common.persistence.asGeometry
import org.around.ieuri.common.persistence.geographyPoint
import org.around.ieuri.common.persistence.stIntersects
import org.around.ieuri.common.persistence.stX
import org.around.ieuri.common.persistence.stY
import org.around.ieuri.tourism.adapter.persistence.mapper.toRegionRow
import org.around.ieuri.tourism.adapter.persistence.table.AreaTable
import org.around.ieuri.tourism.adapter.persistence.table.DistrictTable
import org.around.ieuri.tourism.adapter.persistence.table.KtoConcentrationRateTable
import org.around.ieuri.tourism.adapter.persistence.table.KtoSpendingIntensityTable
import org.around.ieuri.tourism.adapter.persistence.table.KtoStayIntensityTable
import org.around.ieuri.tourism.adapter.persistence.table.KtoVisitorCountTable
import org.around.ieuri.tourism.application.region.required.RegionMetricQueryPort
import org.around.ieuri.tourism.application.region.required.dto.DistrictRegion
import org.around.ieuri.tourism.application.region.required.dto.RegionMetric
import org.around.ieuri.tourism.application.region.required.dto.RegionRow
import org.around.ieuri.tourism.domain.shared.Coordinates
import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.CustomOperator
import org.jetbrains.exposed.v1.core.DoubleColumnType
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.intLiteral
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.core.wrapAsExpression
import org.jetbrains.exposed.v1.javatime.JavaLocalDateColumnType
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 시군구 추천 지표 조회 어댑터(SPEC-0015 R14·R21). 지표마다 질의를 나누고, 지역과 지표의 결합은 서비스가 한다.
 * - "오늘"은 한국 날짜다.
 */
@Component
class RegionMetricQueryAdapter : RegionMetricQueryPort {

    /** origin 좌표를 품은 시군구 1건. 경계 안에 없으면(바다·해외) null이다. 경계선 위 좌표는 2건을 낼 수 있어 하나만 고른다. */
    override fun findDistrictAt(origin: Coordinates): DistrictRegion? = DistrictTable.select(DistrictTable.code, DistrictTable.areaCd)
        .where { stIntersects(DistrictTable.boundary, geographyPoint(origin.longitude, origin.latitude)) }
        .limit(1)
        .singleOrNull()
        ?.let { DistrictRegion(code = it[DistrictTable.code], provinceCode = it[DistrictTable.areaCd]) }

    /**
     * 한 시도의 추천 후보 시군구 전건. 좌표는 `center`가 아니라 대표 위치다.
     * - 대표 위치가 없는 시군구는 후보에서 뺀다 — 추천해 놓고 그 지역에 갈 곳이 없으면 추천이 성립하지 않는다.
     */
    override fun findRegions(provinceCode: String): List<RegionRow> = DistrictTable
        .join(AreaTable, JoinType.INNER, AreaTable.areaCd, DistrictTable.areaCd)
        .select(
            DistrictTable.code, DistrictTable.areaCd,
            DistrictTable.nameKo, DistrictTable.nameEn, DistrictTable.nameJa, DistrictTable.nameZh,
            AreaTable.nameKo, AreaTable.nameEn, AreaTable.nameJa, AreaTable.nameZh,
            REPRESENTATIVE_LATITUDE, REPRESENTATIVE_LONGITUDE,
        )
        .where { (DistrictTable.areaCd eq provinceCode) and DistrictTable.representativeLocation.isNotNull() }
        .orderBy(DistrictTable.code, SortOrder.ASC)
        .map { it.toRegionRow(latitude = it[REPRESENTATIVE_LATITUDE], longitude = it[REPRESENTATIVE_LONGITUDE]) }

    /** 그 시도 시군구별 오늘 예측 집중률의 평균. 오늘 행이 없으면 다른 날로 밀지 않고 결측이다(R14). */
    override fun findConcentrationRates(provinceCode: String): Map<String, RegionMetric> {
        val averageRate = average(KtoConcentrationRateTable.concentrationRate)
        return KtoConcentrationRateTable
            .join(DistrictTable, JoinType.INNER, DistrictTable.code, KtoConcentrationRateTable.districtCode)
            .select(KtoConcentrationRateTable.districtCode, averageRate)
            .where { (DistrictTable.areaCd eq provinceCode) and (KtoConcentrationRateTable.targetDate eq today()) }
            .groupBy(KtoConcentrationRateTable.districtCode)
            .associate { it[KtoConcentrationRateTable.districtCode] to RegionMetric(rawValue = it[averageRate]) }
    }

    /**
     * 그 시도 시군구별 최근 7일 평균 방문자수(외지인+외국인, 현지인 제외).
     * - 7일은 지역별 최신 기준일에서 되짚는다. 일별 합계를 낸 뒤 평균한다 — 순서가 뒤집히면 구분별 평균이 된다.
     */
    override fun findVisitorCounts(provinceCode: String): Map<String, RegionMetric> {
        val visitors = KtoVisitorCountTable
        val sameDistrictVisitors = KtoVisitorCountTable.alias("same_district")
        val latestBaseDate = wrapAsExpression<LocalDate>(
            sameDistrictVisitors.select(sameDistrictVisitors[visitors.baseDate].max())
                .where { sameDistrictVisitors[visitors.districtCode] eq visitors.districtCode },
        )
        val windowStart = CustomOperator<LocalDate>("-", JavaLocalDateColumnType(), latestBaseDate, intLiteral(VISITOR_WINDOW_DAYS))
        val dailyTotal = visitors.touristCount.sum().alias("daily_total")
        val dailyVisitors = visitors.join(DistrictTable, JoinType.INNER, DistrictTable.code, visitors.districtCode)
            .select(visitors.districtCode, visitors.baseDate, dailyTotal)
            .where { (DistrictTable.areaCd eq provinceCode) and (visitors.visitorType inList VISITOR_TYPES) and (visitors.baseDate greater windowStart) }
            .groupBy(visitors.districtCode, visitors.baseDate)
            .alias("daily_visitors")

        val districtCode = dailyVisitors[visitors.districtCode]
        val averageDailyVisitors = average(dailyVisitors[dailyTotal])
        val latestDay = dailyVisitors[visitors.baseDate].max()
        val today = today()
        return dailyVisitors.select(districtCode, averageDailyVisitors, latestDay)
            .groupBy(districtCode)
            .associate { row ->
                row[districtCode] to RegionMetric(
                    rawValue = row[averageDailyVisitors],
                    ageDays = row[latestDay]?.let { ChronoUnit.DAYS.between(it, today) },
                )
            }
    }

    /** 그 시도 시군구별 최신 월 체류 강도. 월 단위라 수십 일 지난 값이 정상이다. */
    override fun findStayIntensities(provinceCode: String): Map<String, RegionMetric> = KtoStayIntensityTable
        .join(DistrictTable, JoinType.INNER, DistrictTable.code, KtoStayIntensityTable.districtCode)
        .select(KtoStayIntensityTable.districtCode, KtoStayIntensityTable.indicatorValue)
        .where { DistrictTable.areaCd eq provinceCode }
        .withDistinctOn(KtoStayIntensityTable.districtCode to SortOrder.ASC)
        .orderBy(KtoStayIntensityTable.districtCode to SortOrder.ASC, KtoStayIntensityTable.baseMonth to SortOrder.DESC)
        .associate { it[KtoStayIntensityTable.districtCode] to RegionMetric(rawValue = it[KtoStayIntensityTable.indicatorValue].toDouble()) }

    /** 그 시도 시군구별 최신 월 소비 강도. 외지인 소비 기준이며 외국인 소비는 포함하지 않는다. */
    override fun findSpendingIntensities(provinceCode: String): Map<String, RegionMetric> = KtoSpendingIntensityTable
        .join(DistrictTable, JoinType.INNER, DistrictTable.code, KtoSpendingIntensityTable.districtCode)
        .select(KtoSpendingIntensityTable.districtCode, KtoSpendingIntensityTable.indicatorValue)
        .where { DistrictTable.areaCd eq provinceCode }
        .withDistinctOn(KtoSpendingIntensityTable.districtCode to SortOrder.ASC)
        .orderBy(KtoSpendingIntensityTable.districtCode to SortOrder.ASC, KtoSpendingIntensityTable.baseMonth to SortOrder.DESC)
        .associate { it[KtoSpendingIntensityTable.districtCode] to RegionMetric(rawValue = it[KtoSpendingIntensityTable.indicatorValue].toDouble()) }

    /** `AVG(expr)`를 실수로 읽는다 — Exposed의 `avg()`는 소수 둘째 자리에서 자른다. */
    private fun average(expression: Expression<*>): ExpressionWithColumnType<Double> = CustomFunction("AVG", DoubleColumnType(), expression)

    private fun today(): LocalDate = LocalDate.now(SEOUL)

    private companion object {
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")

        val REPRESENTATIVE_LATITUDE = stY(DistrictTable.representativeLocation.asGeometry())
        val REPRESENTATIVE_LONGITUDE = stX(DistrictTable.representativeLocation.asGeometry())

        /** 외지인(2)·외국인(3). 현지인(1)은 뺀다. */
        val VISITOR_TYPES = listOf("2", "3")

        const val VISITOR_WINDOW_DAYS = 7
    }
}
