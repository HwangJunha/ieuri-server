package org.around.ieuri.tourism.application.shared.provided.dto

import org.around.ieuri.tourism.domain.shared.AreaName
import org.around.ieuri.tourism.domain.shared.Coordinates
import org.around.ieuri.tourism.domain.shared.RegionScoreFactor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 지표 행의 항목 추출(SPEC-0015 · POL-043). 결측은 0으로 채우지 않고 빠진다. */
class RegionMetricRowTest {

    private val region = RegionRow(
        code = "11680",
        provinceCode = "11",
        name = AreaName(ko = "강남구", en = "Gangnam-gu", ja = null, zh = null),
        provinceName = AreaName(ko = "서울특별시", en = "Seoul", ja = null, zh = null),
        center = Coordinates(latitude = 37.5, longitude = 127.05),
    )

    private fun row(
        concentrationRate: Double? = null,
        visitorCount: Double? = null,
        visitorAgeDays: Long? = null,
        stayIntensity: Double? = null,
        spendingIntensity: Double? = null,
    ) = RegionMetricRow(
        region = region,
        // 결측 지표는 맵에 키 자체가 없다.
        metrics = buildMap {
            concentrationRate?.let {
                put(RegionScoreFactor.CONGESTION, RegionMetric(it))
            }
            visitorCount?.let {
                put(RegionScoreFactor.VISITOR_VOLUME, RegionMetric(it, ageDays = visitorAgeDays))
            }
            stayIntensity?.let {
                put(RegionScoreFactor.STAY_INTENSITY, RegionMetric(it))
            }
            spendingIntensity?.let {
                put(RegionScoreFactor.SPENDING_INTENSITY, RegionMetric(it))
            }
        },
    )

    @Test
    fun `항목마다 자기 값이 실린다`() {
        // 값을 항목마다 다르게 둬 짝이 뒤바뀌면 드러나게 한다.
        val metrics = row(
            concentrationRate = 67.4,
            visitorCount = 633_341.0,
            stayIntensity = 70.5,
            spendingIntensity = 88.25,
        ).metrics

        assertEquals(67.4, metrics.getValue(RegionScoreFactor.CONGESTION).rawValue)
        assertEquals(633_341.0, metrics.getValue(RegionScoreFactor.VISITOR_VOLUME).rawValue)
        assertEquals(70.5, metrics.getValue(RegionScoreFactor.STAY_INTENSITY).rawValue)
        assertEquals(88.25, metrics.getValue(RegionScoreFactor.SPENDING_INTENSITY).rawValue)
    }

    @Test
    fun `방문자수는 7일 평균이라 하루치 최댓값이 아니다`() {
        // 어댑터가 SUM이 아니라 AVG를 싣는다는 계약을 여기서 고정한다.
        val weeklyAverage = 633_341.0

        val metric = row(visitorCount = weeklyAverage, visitorAgeDays = 30)
            .metrics
            .getValue(RegionScoreFactor.VISITOR_VOLUME)

        assertEquals(weeklyAverage, metric.rawValue)
        // 나이는 DB가 계산해 넘긴 값이며 신선도 감쇠가 이 값을 쓴다.
        assertEquals(30L, metric.ageDays)
    }

    @Test
    fun `결측 항목은 목록에서 빠진다 — 0으로 채우지 않는다`() {
        val metrics = row(concentrationRate = 67.4).metrics

        assertEquals(setOf(RegionScoreFactor.CONGESTION), metrics.keys)
        assertNull(metrics[RegionScoreFactor.VISITOR_VOLUME])
    }

    @Test
    fun `값이 하나도 없으면 항목도 비어 있다`() {
        assertTrue(row().metrics.isEmpty())
    }

    @Test
    fun `나이가 실리지 않은 지표는 나이가 null이다 — 감쇠하지 않는다`() {
        val metric = row(visitorCount = 633_341.0, visitorAgeDays = null)
            .metrics
            .getValue(RegionScoreFactor.VISITOR_VOLUME)

        assertEquals(633_341.0, metric.rawValue)
        assertNull(metric.ageDays)
    }

    @Test
    fun `지역 정보는 결합된 지역 행에서 그대로 읽힌다`() {
        // 결합 결과가 어느 지역의 것인지 잃지 않는다.
        val row = row(concentrationRate = 67.4)

        assertEquals("11680", row.code)
        assertEquals("11", row.provinceCode)
        assertEquals("강남구", row.name.ko)
        assertEquals("서울특별시", row.provinceName.ko)
        assertEquals(37.5, row.center.latitude)
        assertEquals(127.05, row.center.longitude)
    }
}
