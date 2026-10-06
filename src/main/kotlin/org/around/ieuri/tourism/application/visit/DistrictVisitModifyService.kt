package org.around.ieuri.tourism.application.visit

import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.tourism.application.shared.provided.dto.toView
import org.around.ieuri.tourism.application.shared.required.MapCellDistrictQueryPort
import org.around.ieuri.tourism.application.shared.required.MapCellQueryPort
import org.around.ieuri.tourism.application.visit.provided.DistrictVisitCommandUseCase
import org.around.ieuri.tourism.application.visit.provided.dto.RecordVisitsCommand
import org.around.ieuri.tourism.application.visit.provided.dto.RecordVisitsResult
import org.around.ieuri.tourism.application.visit.provided.dto.RemoveVisitCommand
import org.around.ieuri.tourism.application.visit.provided.dto.RemoveVisitResult
import org.around.ieuri.tourism.application.visit.required.DistrictVisitQueryPort
import org.around.ieuri.tourism.application.visit.required.DistrictVisitRepository
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.MapCellGrouping
import org.around.ieuri.tourism.domain.visit.DistrictVisits
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 전국 지도 방문 기록 쓰기(SPEC-0030 R3·R4).
 * - 저장하는 것은 시군구 코드뿐이다 — 좌표·기록 시각·판정 출처는 저장하지 않는다(R5, POL-045).
 */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX)
class DistrictVisitModifyService(
    private val visitRepository: DistrictVisitRepository,
    private val districtVisitQueryPort: DistrictVisitQueryPort,
    private val mapCellQueryPort: MapCellQueryPort,
    private val mapCellDistrictQueryPort: MapCellDistrictQueryPort,
) : DistrictVisitCommandUseCase {

    override fun recordVisits(command: RecordVisitsCommand, locale: DomainLocale): RecordVisitsResult {
        val requested = DistrictVisits.of(command.memberId, command.districtCodes)
        if (requested.isEmpty) return RecordVisitsResult(updatedCells = emptyList(), unknownDistrictCodes = emptyList())

        // 없는 코드는 버리지 않고 DISTRICT_NOT_FOUND의 근거로 돌려준다.
        val existing = districtVisitQueryPort.filterExistingDistrictCodes(requested.districtCodes)
        val unknown = requested.districtCodes.filterNot { it in existing }

        val added = visitRepository.record(requested.keepOnly(existing))

        // 이번 요청으로 방문이 된 칸만 돌려준다. 시군구를 한 번에 읽고 도메인이 접는다.
        val updatedCellIds = mapCellDistrictQueryPort.findCellIdsByDistrictCodes(added)
        val visited = districtVisitQueryPort.findVisitedDistrictCodes(command.memberId)
        val updatedCells = MapCellGrouping.fold(mapCellQueryPort.findCellDistrictsIn(updatedCellIds))
            .map { it.toView(locale, visited) }

        return RecordVisitsResult(updatedCells = updatedCells, unknownDistrictCodes = unknown)
    }

    override fun removeVisit(command: RemoveVisitCommand, locale: DomainLocale): RemoveVisitResult {
        // 칸에 속한 시군구 전부를 지운다(R4).
        val rows = mapCellQueryPort.findCellDistricts(command.cellId)
        if (rows.isEmpty()) return RemoveVisitResult(updatedCell = null)

        visitRepository.remove(command.memberId, rows.map { it.districtCode })

        // 이미 읽은 rows로 접는다.
        val visited = districtVisitQueryPort.findVisitedDistrictCodes(command.memberId)
        return RemoveVisitResult(updatedCell = MapCellGrouping.fold(rows).single().toView(locale, visited))
    }

    @Transactional(transactionManager = ExposedConfig.EXPOSED_TX, propagation = Propagation.REQUIRES_NEW)
    override fun eraseMemberContent(memberId: Long): Int = visitRepository.removeAll(memberId)
}
