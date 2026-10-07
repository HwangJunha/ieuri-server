package org.around.ieuri.tourism.application.shared

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.tourism.application.shared.provided.dto.SyncConcentrationRateCommand
import org.around.ieuri.tourism.application.shared.provided.dto.SyncDemandIntensityCommand
import org.around.ieuri.tourism.application.shared.required.DistrictCodeQueryPort
import org.around.ieuri.tourism.application.shared.required.DistrictRegion
import org.around.ieuri.tourism.application.shared.required.RegionMetricStorePort
import org.around.ieuri.tourism.application.shared.required.RegionMetricSyncPage
import org.around.ieuri.tourism.application.shared.required.RegionMetricSyncPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/** KTO 지표 동기화(SPEC-0015 R5·R9) — 미등록 시군구 행은 적재하지 않고 센다. */
class RegionMetricSyncServiceTest {

    private val syncPort = mockk<RegionMetricSyncPort>()
    private val storePort = mockk<RegionMetricStorePort>()
    private val districtCodePort = mockk<DistrictCodeQueryPort>()
    private val service = RegionMetricSyncService(syncPort, storePort, districtCodePort)

    private fun intensity(code: String) = SyncDemandIntensityCommand(code, LocalDate.of(2026, 6, 1), "1", BigDecimal.ONE)

    @Test
    @DisplayName("시도 응답에 섞인 시도 집계 행과 미등록 코드는 건너뛰고 건수만 센다")
    fun `unknown district rows are skipped and counted`() {
        every { syncPort.fetchStayIntensities("11", YearMonth.of(2026, 6), 1, 100) } returns
            RegionMetricSyncPage(listOf(intensity("11110"), intensity("11000"), intensity("99999")), totalCount = 3, droppedCount = 2)
        every { districtCodePort.findAllCodes() } returns setOf("11110")
        every { storePort.upsertStayIntensities(listOf(intensity("11110"))) } returns 1

        val result = service.syncStayIntensities("11", YearMonth.of(2026, 6), 1, 100)

        assertEquals(1, result.written)
        assertEquals(2, result.skippedUnknownCode)
        assertEquals(2, result.dropped)
        assertEquals(3, result.totalCount)
    }

    @Test
    @DisplayName("집중률은 시도 코드와 시군구 코드를 함께 넘긴다")
    fun `concentration passes both province and district codes`() {
        val row = SyncConcentrationRateCommand("11110", "경복궁", LocalDate.of(2026, 8, 2), BigDecimal.TEN)
        every { syncPort.fetchConcentrationRates("11", "11110", 1, 100) } returns RegionMetricSyncPage(listOf(row), totalCount = 1, droppedCount = 0)
        every { districtCodePort.findAllCodes() } returns setOf("11110")
        every { storePort.upsertConcentrationRates(listOf(row)) } returns 1

        assertEquals(1, service.syncConcentrationRates(DistrictRegion(code = "11110", provinceCode = "11"), 1, 100).written)
    }

    @Test
    @DisplayName("빈 응답은 등록 코드도 저장소도 묻지 않는다")
    fun `empty page touches nothing`() {
        every { syncPort.fetchSpendingIntensities("11", YearMonth.of(2026, 6), 1, 100) } returns RegionMetricSyncPage(emptyList(), totalCount = 0, droppedCount = 0)

        val result = service.syncSpendingIntensities("11", YearMonth.of(2026, 6), 1, 100)

        assertEquals(0, result.written)
        verify(exactly = 0) { districtCodePort.findAllCodes() }
        verify(exactly = 0) { storePort.upsertSpendingIntensities(any()) }
    }
}
