package org.around.ieuri.tourism.application.query

import org.around.ieuri.tourism.domain.vo.AreaName
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.RegionScoreFactor
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 지표 행의 항목 추출(SPEC-0015 R14 · POL-043).
 *
 * 정규화 구간 산출·점수 산출·기준일 표기가 전부 [RegionMetricRow.metrics] 하나를 본다 —
 * 값이 있는 항목만 들어가고, 결측은 0으로 채우지 않고 빠진다.
 */
class RegionMetricRowTest {

    private val region = RegionRow(
        code = "11680",
        name = AreaName(ko = "강남구", en = "Gangnam-gu", ja = null, zh = null),
        provinceName = AreaName(ko = "서울특별시", en = "Seoul", ja = null, zh = null),
        center = Coordinates(latitude = 37.5, longitude = 127.05),
    )

    private fun row(
        concentrationRate: Double? = null,
        concentrationBasisDate: LocalDate? = null,
        visitorCount: Double? = null,
        visitorBasisDate: LocalDate? = null,
        stayIntensity: Double? = null,
        stayBasisMonth: LocalDate? = null,
        spendingIntensity: Double? = null,
        spendingBasisMonth: LocalDate? = null,
    ) = RegionMetricRow(
        region = region,
        // 결측 지표는 맵에 키 자체가 없다 — 질의가 그 지역을 돌려주지 않은 상태다.
        metrics = buildMap {
            concentrationRate?.let {
                put(RegionScoreFactor.CONGESTION, RegionMetric(it, concentrationBasisDate))
            }
            visitorCount?.let {
                put(RegionScoreFactor.VISITOR_VOLUME, RegionMetric(it, visitorBasisDate))
            }
            stayIntensity?.let {
                put(RegionScoreFactor.STAY_INTENSITY, RegionMetric(it, stayBasisMonth))
            }
            spendingIntensity?.let {
                put(RegionScoreFactor.SPENDING_INTENSITY, RegionMetric(it, spendingBasisMonth))
            }
        },
    )

    @Test
    fun `항목마다 자기 값과 자기 기준일이 짝지어진다`() {
        // 값과 날짜를 항목마다 다르게 둬 짝이 뒤바뀌면 드러나게 한다.
        val metrics = row(
            concentrationRate = 67.4,
            concentrationBasisDate = LocalDate.of(2026, 8, 11),
            visitorCount = 633_341.0,
            visitorBasisDate = LocalDate.of(2026, 7, 12),
            stayIntensity = 70.5,
            stayBasisMonth = LocalDate.of(2026, 6, 1),
            spendingIntensity = 88.25,
            spendingBasisMonth = LocalDate.of(2026, 5, 1),
        ).metrics

        assertEquals(67.4, metrics.getValue(RegionScoreFactor.CONGESTION).rawValue)
        assertEquals(LocalDate.of(2026, 8, 11), metrics.getValue(RegionScoreFactor.CONGESTION).basisDate)
        assertEquals(633_341.0, metrics.getValue(RegionScoreFactor.VISITOR_VOLUME).rawValue)
        assertEquals(LocalDate.of(2026, 7, 12), metrics.getValue(RegionScoreFactor.VISITOR_VOLUME).basisDate)
        assertEquals(70.5, metrics.getValue(RegionScoreFactor.STAY_INTENSITY).rawValue)
        assertEquals(LocalDate.of(2026, 6, 1), metrics.getValue(RegionScoreFactor.STAY_INTENSITY).basisDate)
        assertEquals(88.25, metrics.getValue(RegionScoreFactor.SPENDING_INTENSITY).rawValue)
        assertEquals(LocalDate.of(2026, 5, 1), metrics.getValue(RegionScoreFactor.SPENDING_INTENSITY).basisDate)
    }

    @Test
    fun `방문자수는 7일 평균이라 하루치 최댓값이 아니다`() {
        // 어댑터가 SUM이 아니라 AVG를 싣는다는 계약을 여기서 고정한다. 강남구 실측 7일
        // 평균이 633,341이며, 하루치 최댓값(금 1,312,557)이 실리면 이 값과 다르다.
        val weeklyAverage = 633_341.0

        val metric = row(
            visitorCount = weeklyAverage,
            visitorBasisDate = LocalDate.of(2026, 7, 12),
        ).metrics.getValue(RegionScoreFactor.VISITOR_VOLUME)

        assertEquals(weeklyAverage, metric.rawValue)
        // 기준일은 평균에 쓰인 7일 중 가장 최근 날이다 — 신선도 감쇠가 이 날짜를 쓴다.
        assertEquals(LocalDate.of(2026, 7, 12), metric.basisDate)
    }

    @Test
    fun `결측 항목은 목록에서 빠진다 — 0으로 채우지 않는다`() {
        val metrics = row(
            concentrationRate = 67.4,
            concentrationBasisDate = LocalDate.of(2026, 8, 11),
        ).metrics

        assertEquals(setOf(RegionScoreFactor.CONGESTION), metrics.keys)
        assertNull(metrics[RegionScoreFactor.VISITOR_VOLUME])
    }

    @Test
    fun `값이 하나도 없으면 항목도 기준일도 비어 있다`() {
        val empty = row()

        assertTrue(empty.metrics.isEmpty())
        assertTrue(empty.basisDates.isEmpty())
    }

    @Test
    fun `값 없이 기준일만 있는 지표는 항목이 되지 않는다`() {
        // 결측 지표의 기준일이 dataBasisAt을 오래된 쪽으로 끌어내리면 안 된다.
        val row = row(
            concentrationRate = 67.4,
            concentrationBasisDate = LocalDate.of(2026, 8, 11),
            visitorCount = null,
            visitorBasisDate = LocalDate.of(2020, 1, 1),
        )

        assertEquals(listOf(LocalDate.of(2026, 8, 11)), row.basisDates)
    }

    @Test
    fun `값은 있고 기준일을 모르면 항목은 남되 기준일이 null이다`() {
        val row = row(visitorCount = 633_341.0, visitorBasisDate = null)

        assertEquals(633_341.0, row.metrics.getValue(RegionScoreFactor.VISITOR_VOLUME).rawValue)
        assertNull(row.metrics.getValue(RegionScoreFactor.VISITOR_VOLUME).basisDate)
        assertTrue(row.basisDates.isEmpty())
    }

    @Test
    fun `지역 정보는 결합된 지역 행에서 그대로 읽힌다`() {
        // 결합 결과가 어느 지역의 것인지 잃지 않는다 — 코드가 어긋나면 점수가 남의 지역에 실린다.
        val row = row(concentrationRate = 67.4, concentrationBasisDate = LocalDate.of(2026, 8, 11))

        assertEquals("11680", row.code)
        assertEquals("강남구", row.name.ko)
        assertEquals("서울특별시", row.provinceName.ko)
        assertEquals(37.5, row.center.latitude)
        assertEquals(127.05, row.center.longitude)
    }
}
