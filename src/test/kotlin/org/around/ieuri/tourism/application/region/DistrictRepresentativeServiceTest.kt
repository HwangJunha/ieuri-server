package org.around.ieuri.tourism.application.region

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.tourism.application.region.required.DistrictRepresentativeUpdatePort
import org.around.ieuri.tourism.domain.region.RepresentativeLocation
import org.around.ieuri.tourism.domain.region.RepresentativeLocation.Point
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class DistrictRepresentativeServiceTest {

    private val port = mockk<DistrictRepresentativeUpdatePort>(relaxed = true)
    private val service = DistrictRepresentativeService(port)

    @Test
    @DisplayName("선정된 시군구만 쓰고, 나머지는 선정된 코드를 빼고 비운다")
    fun `saves the chosen districts and clears the rest`() {
        every { port.findKoreanSpotPoints() } returns mapOf(
            "11110" to listOf(Point(1, 127.0, 37.0), Point(2, 127.0001, 37.0)),
            "26110" to listOf(Point(3, 129.0, 35.0)),
        )
        every { port.clearRepresentativesExcept(setOf("11110")) } returns 4

        val result = service.refreshRepresentativeLocations(cellSizeDegrees = 0.01, minSpotCount = 2)

        verify { port.saveRepresentative("11110", RepresentativeLocation.Choice(spotId = 1, spotCount = 2)) }
        verify(exactly = 0) { port.saveRepresentative("26110", any()) }
        assertEquals(1, result.selected)
        assertEquals(4, result.cleared)
    }
}
