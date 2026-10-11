package org.around.ieuri.tourism.application.region

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.tourism.application.region.required.DistrictRepository
import org.around.ieuri.tourism.domain.region.District
import org.around.ieuri.tourism.domain.region.RepresentativeLocation
import org.around.ieuri.tourism.domain.region.RepresentativeLocation.Point
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class DistrictRepresentativeServiceTest {

    private val districtRepository = mockk<DistrictRepository>(relaxed = true)
    private val service = DistrictRepresentativeService(districtRepository)

    @Test
    @DisplayName("선정된 시군구만 쓰고, 대표 위치가 있던 나머지만 비운다")
    fun `saves the chosen districts and clears the rest`() {
        every { districtRepository.findKoreanSpotPoints(0.01) } returns mapOf(
            "11110" to listOf(Point(1, 127.0, 37.0, cellRank = 1), Point(2, 127.0001, 37.0, cellRank = 1)),
            "26110" to listOf(Point(3, 129.0, 35.0, cellRank = 1)),
        )
        every { districtRepository.findAll() } returns listOf(
            District("11110", hasRepresentative = false),
            District("26110", hasRepresentative = true),
            District("27110", hasRepresentative = true),
            District("28110", hasRepresentative = false),
        )

        val result = service.refreshRepresentativeLocations(cellSizeDegrees = 0.01, minSpotCount = 2)

        verify { districtRepository.saveRepresentative("11110", RepresentativeLocation.Choice(spotId = 1, spotCount = 2)) }
        verify(exactly = 0) { districtRepository.saveRepresentative("26110", any()) }
        verify { districtRepository.clearRepresentative("26110") }
        verify { districtRepository.clearRepresentative("27110") }
        verify(exactly = 0) { districtRepository.clearRepresentative("28110") }
        assertEquals(1, result.selected)
        assertEquals(2, result.cleared)
    }
}
