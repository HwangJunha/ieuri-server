package org.around.ieuri.tourism.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.tourism.application.command.RecordVisitsCommand
import org.around.ieuri.tourism.application.command.RemoveVisitCommand
import org.around.ieuri.tourism.application.policy.NationwideMapPolicy
import org.around.ieuri.tourism.application.port.out.persistence.DistrictVisitStorePort
import org.around.ieuri.tourism.application.port.out.persistence.DistrictVisitUpsertPort
import org.around.ieuri.tourism.application.port.out.persistence.MapShareLinkIssuePort
import org.around.ieuri.tourism.application.port.out.persistence.MapShareLinkPort
import org.around.ieuri.tourism.application.port.out.persistence.MapShareLinkRecord
import org.around.ieuri.tourism.application.port.out.query.DistrictVisitQueryPort
import org.around.ieuri.tourism.application.port.out.query.MapCellDistrictQueryPort
import org.around.ieuri.tourism.application.port.out.query.MapCellQueryPort
import org.around.ieuri.tourism.domain.vo.AreaName
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.MapCellDistrictRow
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 방문 기록과 공유 링크 발급(SPEC-0030 R3·R4·R12·R14). */
class NationwideMapCommandServiceTest {

    private val storePort = mockk<DistrictVisitStorePort>()
    private val upsertPort = mockk<DistrictVisitUpsertPort>()
    private val visitQueryPort = mockk<DistrictVisitQueryPort>()
    private val mapCellQueryPort = mockk<MapCellQueryPort>()
    private val mapCellDistrictQueryPort = mockk<MapCellDistrictQueryPort>()
    private val shareLinkPort = mockk<MapShareLinkPort>()
    private val shareLinkIssuePort = mockk<MapShareLinkIssuePort>()

    private val service = NationwideMapCommandService(
        districtVisitStorePort = storePort,
        districtVisitUpsertPort = upsertPort,
        districtVisitQueryPort = visitQueryPort,
        mapCellQueryPort = mapCellQueryPort,
        mapCellDistrictQueryPort = mapCellDistrictQueryPort,
        mapShareLinkPort = shareLinkPort,
        mapShareLinkIssuePort = shareLinkIssuePort,
        policy = NationwideMapPolicy(
            maxCellSpotPageSize = 50,
            shareUrlBase = "https://www.ieuri.co.kr/tour/korea-map",
            maxRelationPageSize = 50,
        ),
    )

    @Test
    fun `이미 방문한 코드는 갱신된 칸에 포함되지 않는다`() {
        every { visitQueryPort.filterExistingDistrictCodes(any()) } returns setOf("11110", "41111")
        // 11110은 이미 있어 삽입되지 않았다 — 저장소가 새로 들어간 것만 돌려준다.
        every { upsertPort.record(7L, setOf("11110", "41111")) } returns setOf("41111")
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
        every { upsertPort.record(7L, setOf("11110")) } returns emptySet()
        every { mapCellDistrictQueryPort.findCellIdsByDistrictCodes(emptySet()) } returns emptyList()
        every { visitQueryPort.findVisitedDistrictCodes(7L) } returns setOf("11110")
        every { mapCellQueryPort.findCellDistrictsIn(emptyList<Long>()) } returns emptyList()

        val result = service.recordVisits(RecordVisitsCommand(7L, listOf("11110")), DomainLocale.KO)

        assertTrue(result.updatedCells.isEmpty())
        // 멱등이므로 오류가 아니다.
        assertTrue(result.unknownDistrictCodes.isEmpty())
    }

    @Test
    fun `없는 시군구 코드는 조용히 버리지 않고 알린다`() {
        every { visitQueryPort.filterExistingDistrictCodes(any()) } returns setOf("11110")
        every { upsertPort.record(7L, setOf("11110")) } returns setOf("11110")
        every { mapCellDistrictQueryPort.findCellIdsByDistrictCodes(setOf("11110")) } returns listOf(1L)
        every { visitQueryPort.findVisitedDistrictCodes(7L) } returns setOf("11110")
        every { mapCellQueryPort.findCellDistrictsIn(listOf(1L)) } returns listOf(row(1L, "11110"))

        val result = service.recordVisits(RecordVisitsCommand(7L, listOf("11110", "99999")), DomainLocale.KO)

        assertEquals(listOf("99999"), result.unknownDistrictCodes)
        // 나머지는 정상 기록된다 — 코드 하나 때문에 전체가 실패하지 않는다.
        assertEquals(listOf(1L), result.updatedCells.map { it.id })
    }

    @Test
    fun `중복 코드는 한 번만 저장된다`() {
        every { visitQueryPort.filterExistingDistrictCodes(any()) } returns setOf("11110")
        every { upsertPort.record(7L, setOf("11110")) } returns setOf("11110")
        every { mapCellDistrictQueryPort.findCellIdsByDistrictCodes(any()) } returns listOf(1L)
        every { visitQueryPort.findVisitedDistrictCodes(7L) } returns setOf("11110")
        every { mapCellQueryPort.findCellDistrictsIn(listOf(1L)) } returns listOf(row(1L, "11110"))

        service.recordVisits(RecordVisitsCommand(7L, listOf("11110", "11110", "11110")), DomainLocale.KO)

        verify(exactly = 1) { visitQueryPort.filterExistingDistrictCodes(listOf("11110")) }
    }

    @Test
    fun `빈 목록은 저장을 시도하지 않는다`() {
        val result = service.recordVisits(RecordVisitsCommand(7L, emptyList()), DomainLocale.KO)

        assertTrue(result.updatedCells.isEmpty())
        verify(exactly = 0) { upsertPort.record(any(), any()) }
    }

    @Test
    fun `칸을 해제하면 그 칸의 시군구 전부가 지워진다`() {
        every { mapCellQueryPort.findCellDistricts(2L) } returns
            listOf("41111", "41113", "41115", "41117").map { row(2L, it) }
        every { storePort.remove(7L, any()) } returns Unit
        every { visitQueryPort.findVisitedDistrictCodes(7L) } returns emptySet()

        service.removeVisit(RemoveVisitCommand(7L, 2L), DomainLocale.KO)

        verify { storePort.remove(7L, listOf("41111", "41113", "41115", "41117")) }
    }

    @Test
    fun `없는 칸의 해제는 오류가 아니라 갱신된 칸이 null이다`() {
        every { mapCellQueryPort.findCellDistricts(999L) } returns emptyList()

        val result = service.removeVisit(RemoveVisitCommand(7L, 999L), DomainLocale.KO)

        assertNull(result.updatedCell)
        verify(exactly = 0) { storePort.remove(any(), any()) }
    }

    @Test
    fun `공유 링크는 발급을 반복해도 같은 URL이다`() {
        val existing = MapShareLinkRecord(7L, "already-issued", Instant.parse("2026-08-01T00:00:00Z"))
        // 새 토큰을 만들어 넘겨도 저장소가 기존 행을 돌려준다.
        every { shareLinkIssuePort.findOrCreate(eq(7L), any()) } returns existing

        val expected = "https://www.ieuri.co.kr/tour/korea-map/already-issued"
        assertEquals(expected, service.createShareLink(7L).shareUrl)
        assertEquals(expected, service.createShareLink(7L).shareUrl)
    }

    @Test
    fun `폐기는 URL에서 토큰을 꺼내 넘긴다`() {
        every { shareLinkPort.revoke(7L, "kJ3xQ9") } returns true

        assertTrue(service.revokeShareLink(7L, "https://www.ieuri.co.kr/tour/korea-map/kJ3xQ9"))

        // 도메인·경로가 바뀌어도 마지막 조각이 토큰이라 그대로 동작한다.
        assertTrue(service.revokeShareLink(7L, "https://ieuri.co.kr/shared/kJ3xQ9"))
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
