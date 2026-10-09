package org.around.ieuri.tourism.application.spot

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.around.ieuri.tourism.application.spot.required.FestivalSyncPort
import org.around.ieuri.tourism.application.spot.required.SpotRepository
import org.around.ieuri.tourism.application.spot.required.SpotSyncPort
import org.around.ieuri.tourism.application.spot.required.dto.FestivalRecord
import org.around.ieuri.tourism.application.spot.required.dto.FestivalSyncPage
import org.around.ieuri.tourism.application.spot.required.dto.SpotSyncPage
import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.Coordinates
import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.FestivalPeriod
import org.around.ieuri.tourism.domain.spot.Spot
import org.around.ieuri.tourism.domain.spot.SpotFestival
import org.around.ieuri.tourism.domain.spot.SpotHarvest
import org.around.ieuri.tourism.domain.spot.SweepBlockedException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.LocalDate

class SpotSyncServiceTest {

    private val spotSyncPort = mockk<SpotSyncPort>()
    private val festivalSyncPort = mockk<FestivalSyncPort>()
    private val repository = mockk<SpotRepository>(relaxed = true)
    private val service = SpotSyncService(spotSyncPort, festivalSyncPort, repository)

    private val syncedAt = Instant.parse("2026-08-01T00:00:00Z")
    private val runDate = LocalDate.of(2026, 8, 1)

    private fun spot(id: String, category: SpotCategory = SpotCategory.TOURIST_SPOT) = Spot(
        contentId = id,
        language = DomainLocale.KO,
        title = "경복궁",
        address = null,
        thumbnailImageUrl = "https://img/$id.jpg",
        coordinates = Coordinates(latitude = 37.56, longitude = 126.98),
        category = category,
        contentTypeId = 12,
        copyright = CopyrightType.TYPE1,
    )

    @Test
    @DisplayName("끝난 축제는 적재하지 않되 이 페이지가 본 contentId에는 남긴다 — 빠지면 정리가 다시 지운다")
    fun `ended festivals are not stored but still reported as seen`() {
        val records = listOf(spot("a"), spot("ended", SpotCategory.FESTIVAL_EVENT))
        every { spotSyncPort.fetchPage(DomainLocale.KO, 1, 100) } returns SpotSyncPage(records, totalCount = 250, droppedCount = 1, categoryFallbackCount = 2, excludedCount = 3)
        // 끝났는지는 도메인이 기간으로 판정한다 — 종료일이 실행일 전이면 끝났다.
        every { repository.findFestivalPeriods(DomainLocale.KO) } returns mapOf("ended" to FestivalPeriod(runDate.minusDays(5), runDate.minusDays(1)))
        every { repository.upsertSpots(listOf(spot("a")), syncedAt) } returns 1

        val result = service.syncSpotPage(DomainLocale.KO, 1, 100, syncedAt, runDate)

        assertEquals(listOf("a", "ended"), result.contentIds)
        assertEquals(1, result.written)
        assertTrue(result.hasMore)
        assertEquals(1, result.dropped)
        assertEquals(2, result.categoryFallback)
        assertEquals(3, result.excluded)
    }

    @Test
    @DisplayName("원본 정리는 도메인이 허락한 생존 집합으로만 지운다")
    fun `sweep deletes with the survivors the harvest allows`() {
        every { repository.deleteMissingSpots(DomainLocale.KO, setOf("a")) } returns 4

        assertEquals(4, service.sweepSpots(DomainLocale.KO, SpotHarvest(setOf("a"), resumed = false)))
    }

    @Test
    @DisplayName("막힌 정리는 아무것도 지우지 않는다")
    fun `a blocked sweep deletes nothing`() {
        assertThrows<SweepBlockedException> { service.sweepSpots(DomainLocale.KO, SpotHarvest(emptySet(), resumed = false)) }
        assertThrows<SweepBlockedException> { service.sweepFestivals(DomainLocale.KO, SpotHarvest(setOf("f"), resumed = true), runDate) }

        verify(exactly = 0) { repository.deleteMissingSpots(any(), any()) }
        verify(exactly = 0) { repository.deleteSpots(any(), any()) }
    }

    @Test
    @DisplayName("원본이 없는 축제 행은 쓰지 않는다")
    fun `festival rows without an origin are skipped`() {
        val kept = FestivalRecord("f1", DomainLocale.KO, FestivalPeriod(runDate, runDate.plusDays(3)), "진행", "문화")
        val orphan = FestivalRecord("f2", DomainLocale.KO, FestivalPeriod(runDate, runDate.plusDays(1)), null, null)
        every { festivalSyncPort.fetchPage(DomainLocale.KO, runDate, 1, 100) } returns FestivalSyncPage(listOf(kept, orphan), totalCount = 2, droppedCount = 0)
        every { repository.findByContentIds(DomainLocale.KO, listOf("f1", "f2")) } returns listOf(spot("f1").copy(id = 41L))
        every { repository.upsertFestivals(any(), syncedAt) } answers { firstArg<List<*>>().size }

        val page = service.syncFestivalPage(DomainLocale.KO, 1, 100, syncedAt, runDate)

        verify { repository.upsertFestivals(listOf(SpotFestival(41L, kept.period, "진행", "문화")), syncedAt) }
        assertEquals(1, page.written)
        assertEquals(listOf("f1", "f2"), page.contentIds)
    }

    @Test
    @DisplayName("축제는 실행일 기준으로 받고, 정리는 끝난 축제 → 사라진 축제 순이다")
    fun `festival page uses the run date and sweep removes ended first`() {
        val record = FestivalRecord("f1", DomainLocale.KO, FestivalPeriod(runDate, runDate.plusDays(3)), null, null)
        every { festivalSyncPort.fetchPage(DomainLocale.KO, runDate, 1, 100) } returns FestivalSyncPage(listOf(record), totalCount = 1, droppedCount = 0)
        every { repository.findByContentIds(DomainLocale.KO, listOf("f1")) } returns listOf(spot("f1").copy(id = 41L))
        every { repository.upsertFestivals(listOf(SpotFestival(41L, record.period, null, null)), syncedAt) } returns 1
        every { repository.findFestivalPeriods(DomainLocale.KO) } returns mapOf(
            "old" to FestivalPeriod(runDate.minusDays(9), runDate.minusDays(1)),
            "today" to FestivalPeriod(runDate.minusDays(2), runDate),
            "f1" to FestivalPeriod(runDate, runDate.plusDays(3)),
        )
        every { repository.deleteSpots(DomainLocale.KO, setOf("old")) } returns 2
        every { repository.deleteMissingFestivals(DomainLocale.KO, setOf("f1")) } returns 3

        val page = service.syncFestivalPage(DomainLocale.KO, 1, 100, syncedAt, runDate)
        val swept = service.sweepFestivals(DomainLocale.KO, SpotHarvest(setOf("f1"), resumed = false), runDate)

        assertFalse(page.hasMore)
        assertEquals(listOf("f1"), page.contentIds)
        assertEquals(2, swept.ended)
        assertEquals(3, swept.missing)
        verifyOrder {
            repository.deleteSpots(DomainLocale.KO, setOf("old"))
            repository.deleteMissingFestivals(DomainLocale.KO, setOf("f1"))
        }
    }
}
