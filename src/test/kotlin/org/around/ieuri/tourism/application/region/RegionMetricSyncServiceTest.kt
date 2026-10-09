package org.around.ieuri.tourism.application.region

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.tourism.application.region.provided.dto.RegionMetricDataset
import org.around.ieuri.tourism.application.region.provided.dto.RegionMetricTarget
import org.around.ieuri.tourism.application.region.required.DistrictCodeQueryPort
import org.around.ieuri.tourism.application.region.required.DistrictRepository
import org.around.ieuri.tourism.application.region.required.RegionMetricSyncPort
import org.around.ieuri.tourism.application.region.required.dto.DemandIntensityRecord
import org.around.ieuri.tourism.application.region.required.dto.DistrictRegion
import org.around.ieuri.tourism.application.region.required.dto.RegionMetricSyncPage
import org.around.ieuri.tourism.application.region.required.dto.VisitorCountRecord
import org.around.ieuri.tourism.domain.region.District
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** RegionMetricSyncService — 종별 순회 대상, 수집 창, 미등록 코드 거름(R9), 월 지표의 기준월 탐색. */
class RegionMetricSyncServiceTest {

    private val syncPort = mockk<RegionMetricSyncPort>()
    private val districtRepository = mockk<DistrictRepository>()
    private val districtCodePort = mockk<DistrictCodeQueryPort>()
    private val policy = RegionMetricSyncPolicy(pageSize = 2, visitorBackfillDays = 3, monthlyLookbackMonths = 3)
    private val service = RegionMetricSyncService(syncPort, districtRepository, districtCodePort, policy)

    private val province = RegionMetricTarget(provinceCode = "11", districtCode = null)

    init {
        every { districtRepository.findAll() } returns listOf(District("11110", hasRepresentative = false), District("11140", hasRepresentative = true))
        every { districtRepository.upsertVisitorCounts(any()) } answers { firstArg<List<*>>().size }
        every { districtRepository.upsertStayIntensities(any()) } answers { firstArg<List<*>>().size }
        every { districtRepository.upsertSpendingIntensities(any()) } answers { firstArg<List<*>>().size }
    }

    private fun visitor(districtCode: String) = VisitorCountRecord(districtCode, RUN_DATE, "1", BigDecimal.TEN)

    private fun intensity(districtCode: String, month: YearMonth) = DemandIntensityRecord(districtCode, month.atDay(1), "A", BigDecimal.ONE)

    @Test
    fun `순회 대상은 종마다 다르다 - 방문자수는 전국 1회, 집중률은 시군구, 강도는 시도`() {
        every { districtCodePort.findAllRegions() } returns listOf(DistrictRegion(code = "11110", provinceCode = "11"))
        every { districtCodePort.findAllProvinceCodes() } returns listOf("11", "26")

        assertEquals(listOf(RegionMetricTarget.NATIONWIDE), service.targets(RegionMetricDataset.VISITOR_COUNT))
        assertEquals(listOf(RegionMetricTarget("11", "11110")), service.targets(RegionMetricDataset.CONCENTRATION_RATE))
        assertEquals(
            listOf(RegionMetricTarget("11", null), RegionMetricTarget("26", null)),
            service.targets(RegionMetricDataset.SPENDING_INTENSITY),
        )
    }

    @Test
    fun `방문자수는 기준일을 끝으로 보충 일수만큼의 구간을 수집한다`() {
        every { syncPort.fetchVisitorCounts(any(), any(), any(), any()) } returns RegionMetricSyncPage(emptyList(), totalCount = 0, droppedCount = 0)

        service.syncPage(RegionMetricDataset.VISITOR_COUNT, RegionMetricTarget.NATIONWIDE, 1, RUN_DATE, null)

        verify { syncPort.fetchVisitorCounts(RUN_DATE.minusDays(2), RUN_DATE, 1, 2) }
    }

    @Test
    fun `등록되지 않은 시군구 행은 적재하지 않고 세며, 버린 행 수와 남은 페이지를 함께 돌려준다`() {
        every { syncPort.fetchVisitorCounts(any(), any(), any(), any()) } returns
            RegionMetricSyncPage(listOf(visitor("11110"), visitor("99999")), totalCount = 5, droppedCount = 4)

        val result = service.syncPage(RegionMetricDataset.VISITOR_COUNT, RegionMetricTarget.NATIONWIDE, 1, RUN_DATE, null)

        verify { districtRepository.upsertVisitorCounts(listOf(District("11110", hasRepresentative = false).visitorCount(RUN_DATE, "1", BigDecimal.TEN))) }
        assertEquals(1, result.written)
        assertEquals(1, result.skippedUnknownCode)
        assertEquals(4, result.dropped)
        assertTrue(result.hasMore)
        assertNull(result.baseMonth)
    }

    @Test
    fun `빈 페이지는 시군구 코드를 읽지도 적재하지도 않는다`() {
        every { syncPort.fetchVisitorCounts(any(), any(), any(), any()) } returns RegionMetricSyncPage(emptyList(), totalCount = 0, droppedCount = 0)

        val result = service.syncPage(RegionMetricDataset.VISITOR_COUNT, RegionMetricTarget.NATIONWIDE, 1, RUN_DATE, null)

        assertEquals(0, result.written)
        assertFalse(result.hasMore)
        verify(exactly = 0) { districtRepository.findAll() }
        verify(exactly = 0) { districtRepository.upsertVisitorCounts(any()) }
    }

    @Test
    fun `기준월이 없으면 이번 달부터 거슬러 올라가 자료가 있는 첫 달을 기준월로 정한다`() {
        val thisMonth = YearMonth.from(RUN_DATE)
        val lastMonth = thisMonth.minusMonths(1)
        every { syncPort.fetchStayIntensities("11", thisMonth, 1, 2) } returns RegionMetricSyncPage(emptyList(), totalCount = 0, droppedCount = 0)
        every { syncPort.fetchStayIntensities("11", lastMonth, 1, 2) } returns
            RegionMetricSyncPage(listOf(intensity("11110", lastMonth)), totalCount = 1, droppedCount = 0)

        val result = service.syncPage(RegionMetricDataset.STAY_INTENSITY, province, 1, RUN_DATE, null)

        assertEquals(lastMonth, result.baseMonth)
        assertEquals(1, result.written)
        verify(exactly = 0) { syncPort.fetchStayIntensities("11", thisMonth.minusMonths(2), any(), any()) }
    }

    @Test
    fun `창 안에 자료가 없으면 기준월 없이 0건으로 끝난다`() {
        every { syncPort.fetchSpendingIntensities(any(), any(), any(), any()) } returns RegionMetricSyncPage(emptyList(), totalCount = 0, droppedCount = 0)

        val result = service.syncPage(RegionMetricDataset.SPENDING_INTENSITY, province, 1, RUN_DATE, null)

        assertNull(result.baseMonth)
        assertEquals(0, result.written)
        assertFalse(result.hasMore)
        verify(exactly = 3) { syncPort.fetchSpendingIntensities("11", any(), 1, 2) }
    }

    @Test
    fun `정해진 기준월은 비어 있어도 다시 찾지 않고 그 달만 묻는다`() {
        val fixed = YearMonth.of(2026, 5)
        every { syncPort.fetchSpendingIntensities("11", fixed, 2, 2) } returns RegionMetricSyncPage(emptyList(), totalCount = 0, droppedCount = 0)

        val result = service.syncPage(RegionMetricDataset.SPENDING_INTENSITY, province, 2, RUN_DATE, fixed)

        assertEquals(fixed, result.baseMonth)
        verify(exactly = 1) { syncPort.fetchSpendingIntensities(any(), any(), any(), any()) }
    }

    private companion object {
        val RUN_DATE: LocalDate = LocalDate.of(2026, 8, 10)
    }
}
