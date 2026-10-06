package org.around.ieuri.tourism.application.visit

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.tourism.application.shared.required.MapCellDistrictQueryPort
import org.around.ieuri.tourism.application.shared.required.MapCellQueryPort
import org.around.ieuri.tourism.application.visit.provided.dto.RecordVisitsCommand
import org.around.ieuri.tourism.application.visit.provided.dto.RemoveVisitCommand
import org.around.ieuri.tourism.application.visit.required.DistrictVisitQueryPort
import org.around.ieuri.tourism.application.visit.required.DistrictVisitRepository
import org.around.ieuri.tourism.domain.shared.AreaName
import org.around.ieuri.tourism.domain.shared.Coordinates
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.MapCellDistrictRow
import org.around.ieuri.tourism.domain.visit.DistrictVisits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DistrictVisitModifyServiceTest {

    private val repository = mockk<DistrictVisitRepository>()
    private val visitQueryPort = mockk<DistrictVisitQueryPort>()
    private val mapCellQueryPort = mockk<MapCellQueryPort>()
    private val mapCellDistrictQueryPort = mockk<MapCellDistrictQueryPort>()

    private val service = DistrictVisitModifyService(repository, visitQueryPort, mapCellQueryPort, mapCellDistrictQueryPort)

    @Test
    fun `이미 방문한 코드는 갱신된 칸에 포함되지 않는다`() {
        every { visitQueryPort.filterExistingDistrictCodes(any()) } returns setOf("11110", "41111")
        // 11110은 이미 있어 삽입되지 않았다 — 저장소가 새로 들어간 것만 돌려준다.
        every { repository.record(DistrictVisits(7L, listOf("11110", "41111"))) } returns setOf("41111")
        every { mapCellDistrictQueryPort.findCellIdsByDistrictCodes(setOf("41111")) } returns listOf(2L)
        every { visitQueryPort.findVisitedDistrictCodes(7L) } returns setOf("11110", "41111")
        every { mapCellQueryPort.findCellDistrictsIn(listOf(2L)) } returns listOf(row(2L, "41111"))

        val result = service.recordVisits(RecordVisitsCommand(7L, listOf("11110", "41111")), DomainLocale.KO)

        assertEquals(listOf(2L), result.updatedCells.map { it.id })
        assertTrue(result.unknownDistrictCodes.isEmpty())
    }

    @Test
    fun `같은 코드를 두 번 등록해도 두 번째는 갱신된 칸이 없다`() {
        every { visitQueryPort.filterExistingDistrictCodes(any()) } returns setOf("11110")
        every { repository.record(DistrictVisits(7L, listOf("11110"))) } returns emptySet()
        every { mapCellDistrictQueryPort.findCellIdsByDistrictCodes(emptySet()) } returns emptyList()
        every { visitQueryPort.findVisitedDistrictCodes(7L) } returns setOf("11110")
        every { mapCellQueryPort.findCellDistrictsIn(emptyList<Long>()) } returns emptyList()

        val result = service.recordVisits(RecordVisitsCommand(7L, listOf("11110")), DomainLocale.KO)

        assertTrue(result.updatedCells.isEmpty())
        // 멱등이므로 오류가 아니다.
        assertTrue(result.unknownDistrictCodes.isEmpty())
    }

    @Test
    fun `없는 시군구 코드는 조용히 버리지 않고 알리고 나머지만 기록한다`() {
        every { visitQueryPort.filterExistingDistrictCodes(any()) } returns setOf("11110")
        every { repository.record(DistrictVisits(7L, listOf("11110"))) } returns setOf("11110")
        every { mapCellDistrictQueryPort.findCellIdsByDistrictCodes(setOf("11110")) } returns listOf(1L)
        every { visitQueryPort.findVisitedDistrictCodes(7L) } returns setOf("11110")
        every { mapCellQueryPort.findCellDistrictsIn(listOf(1L)) } returns listOf(row(1L, "11110"))

        val result = service.recordVisits(RecordVisitsCommand(7L, listOf("11110", "99999")), DomainLocale.KO)

        assertEquals(listOf("99999"), result.unknownDistrictCodes)
        // 코드 하나 때문에 전체가 실패하지 않는다 — 없는 코드는 저장소로 가지 않는다.
        assertEquals(listOf(1L), result.updatedCells.map { it.id })
        verify(exactly = 1) { repository.record(DistrictVisits(7L, listOf("11110"))) }
    }

    @Test
    fun `중복 코드는 한 번만 조회·저장된다`() {
        every { visitQueryPort.filterExistingDistrictCodes(any()) } returns setOf("11110")
        every { repository.record(any()) } returns setOf("11110")
        every { mapCellDistrictQueryPort.findCellIdsByDistrictCodes(any()) } returns listOf(1L)
        every { visitQueryPort.findVisitedDistrictCodes(7L) } returns setOf("11110")
        every { mapCellQueryPort.findCellDistrictsIn(listOf(1L)) } returns listOf(row(1L, "11110"))

        service.recordVisits(RecordVisitsCommand(7L, listOf("11110", "11110", "11110")), DomainLocale.KO)

        verify(exactly = 1) { visitQueryPort.filterExistingDistrictCodes(listOf("11110")) }
        verify(exactly = 1) { repository.record(DistrictVisits(7L, listOf("11110"))) }
    }

    @Test
    fun `빈 목록과 빈 코드뿐인 목록은 저장을 시도하지 않는다`() {
        assertTrue(service.recordVisits(RecordVisitsCommand(7L, emptyList()), DomainLocale.KO).updatedCells.isEmpty())
        assertTrue(service.recordVisits(RecordVisitsCommand(7L, listOf(" ", "")), DomainLocale.KO).updatedCells.isEmpty())

        verify(exactly = 0) { repository.record(any()) }
    }

    @Test
    fun `칸을 해제하면 그 칸의 시군구 전부가 지워진다`() {
        every { mapCellQueryPort.findCellDistricts(2L) } returns listOf("41111", "41113", "41115", "41117").map { row(2L, it) }
        every { repository.remove(7L, any()) } returns Unit
        every { visitQueryPort.findVisitedDistrictCodes(7L) } returns emptySet()

        service.removeVisit(RemoveVisitCommand(7L, 2L), DomainLocale.KO)

        verify { repository.remove(7L, listOf("41111", "41113", "41115", "41117")) }
    }

    @Test
    fun `없는 칸의 해제는 오류가 아니라 갱신된 칸이 null이다`() {
        every { mapCellQueryPort.findCellDistricts(999L) } returns emptyList()

        val result = service.removeVisit(RemoveVisitCommand(7L, 999L), DomainLocale.KO)

        assertNull(result.updatedCell)
        verify(exactly = 0) { repository.remove(any(), any()) }
    }

    @Test
    fun `탈퇴 정리는 그 회원의 방문 기록을 전부 지우고 건수를 돌려준다`() {
        every { repository.removeAll(7L) } returns 12

        assertEquals(12, service.eraseMemberContent(7L))
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
