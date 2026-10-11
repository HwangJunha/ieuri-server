package org.around.ieuri.tourism.application.footprint

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.tourism.application.footprint.provided.dto.RecordVisitsCommand
import org.around.ieuri.tourism.application.footprint.provided.dto.RemoveVisitCommand
import org.around.ieuri.tourism.application.footprint.required.FootprintQueryPort
import org.around.ieuri.tourism.application.footprint.required.FootprintRepository
import org.around.ieuri.tourism.application.footprint.required.MapCellDistrictQueryPort
import org.around.ieuri.tourism.application.footprint.required.MapCellQueryPort
import org.around.ieuri.tourism.domain.footprint.Footprint
import org.around.ieuri.tourism.domain.footprint.MapCellDistrictRow
import org.around.ieuri.tourism.domain.footprint.ShareLink
import org.around.ieuri.tourism.domain.shared.AreaName
import org.around.ieuri.tourism.domain.shared.Coordinates
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

class FootprintModifyServiceTest {

    private val repository = mockk<FootprintRepository>(relaxed = true)
    private val queryPort = mockk<FootprintQueryPort>()
    private val mapCellQueryPort = mockk<MapCellQueryPort>()
    private val mapCellDistrictQueryPort = mockk<MapCellDistrictQueryPort>()
    private val service = FootprintModifyService(
        repository,
        queryPort,
        mapCellQueryPort,
        mapCellDistrictQueryPort,
        FootprintPolicy(maxCellSpotPageSize = 50, shareUrlBase = "https://www.ieuri.co.kr/tour/korea-map"),
    )

    private val issued = ShareLink("already-issued", Instant.parse("2026-08-01T00:00:00Z"))

    @Test
    @DisplayName("새로 방문이 된 코드만 저장하고 그 칸만 돌려준다")
    fun `record stores only added codes and returns their cells`() {
        every { repository.findByMemberId(7) } returns Footprint(7, setOf("11110"), null)
        every { queryPort.filterExistingDistrictCodes(listOf("11110", "41111", "99999")) } returns setOf("11110", "41111")
        every { mapCellDistrictQueryPort.findCellIdsByDistrictCodes(listOf("41111")) } returns listOf(2L)
        every { mapCellQueryPort.findCellDistrictsIn(listOf(2L)) } returns listOf(row(2, "41111"))

        val result = service.recordVisits(RecordVisitsCommand(listOf("11110", "41111", "99999")), 7, DomainLocale.KO)

        verify { repository.addVisits(7, listOf("41111")) }
        assertEquals(listOf(2L), result.updatedCells.map { it.id })
        assertTrue(result.updatedCells.single().viewerVisited)
        assertEquals(listOf("99999"), result.unknownDistrictCodes)
    }

    @Test
    @DisplayName("칸을 해제하면 그 칸에서 방문한 시군구만 지우고, 해제된 칸을 돌려준다")
    fun `remove deletes the visited codes of the cell`() {
        every { mapCellQueryPort.findCellDistricts(2) } returns listOf(row(2, "41111"), row(2, "41113"))
        every { repository.findByMemberId(7) } returns Footprint(7, setOf("41111", "11110"), null)

        val result = service.removeVisit(RemoveVisitCommand(2), 7, DomainLocale.KO)

        verify { repository.removeVisits(7, setOf("41111")) }
        assertEquals(2L, result.updatedCell?.id)
        assertFalse(requireNotNull(result.updatedCell).viewerVisited)
    }

    @Test
    @DisplayName("없는 칸의 해제는 오류가 아니라 갱신된 칸이 null이다")
    fun `removing a missing cell returns null`() {
        every { mapCellQueryPort.findCellDistricts(999) } returns emptyList()

        assertNull(service.removeVisit(RemoveVisitCommand(999), 7, DomainLocale.KO).updatedCell)
        verify(exactly = 0) { repository.removeVisits(any(), any()) }
    }

    @Test
    @DisplayName("공유 링크는 저장된 링크의 토큰을 설정된 앞부분에 붙여 돌려준다")
    fun `share link url is built from the stored token`() {
        every { repository.findByMemberId(7) } returns Footprint(7, emptySet(), issued)
        every { repository.saveShareLink(7, issued) } returns issued

        val view = service.createShareLink(7)

        assertEquals("https://www.ieuri.co.kr/tour/korea-map/already-issued", view.shareUrl)
        assertEquals(issued.issuedAt, view.createdAt)
    }

    @Test
    @DisplayName("폐기는 URL의 마지막 조각을 토큰으로 본다")
    fun `revoke takes the token from the url`() {
        every { repository.findByMemberId(7) } returns Footprint(7, emptySet(), issued)
        every { repository.revokeShareLink(7, issued) } returns true

        assertTrue(service.revokeShareLink(7, "https://ieuri.co.kr/shared/already-issued?x=1"))
    }

    @Test
    @DisplayName("남의 링크나 이미 폐기된 링크는 저장소에 가지 않고 false다")
    fun `revoking a foreign token is false`() {
        every { repository.findByMemberId(7) } returns Footprint(7, emptySet(), issued)

        assertFalse(service.revokeShareLink(7, "https://www.ieuri.co.kr/tour/korea-map/other"))
        verify(exactly = 0) { repository.revokeShareLink(any(), any()) }
    }

    @Test
    @DisplayName("탈퇴 정리는 방문과 링크를 지운 수를 돌려준다")
    fun `erase deletes visits and links`() {
        every { repository.deleteAll(7) } returns 13

        assertEquals(13, service.eraseMemberContent(7))
    }

    private fun row(cellId: Long, districtCode: String) = MapCellDistrictRow(
        cellId = cellId,
        districtCode = districtCode,
        cellName = AreaName("칸", "cell", null, null),
        provinceName = AreaName("시도", "province", null, null),
        provinceCode = districtCode.take(2),
        center = Coordinates(37.5, 127.0),
    )
}
