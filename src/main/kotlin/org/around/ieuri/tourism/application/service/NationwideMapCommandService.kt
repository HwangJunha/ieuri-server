package org.around.ieuri.tourism.application.service

import org.around.ieuri.tourism.application.command.RecordVisitsCommand
import org.around.ieuri.tourism.application.command.RecordVisitsResult
import org.around.ieuri.tourism.application.command.RemoveVisitCommand
import org.around.ieuri.tourism.application.command.RemoveVisitResult
import org.around.ieuri.tourism.application.port.`in`.command.NationwideMapCommandUseCase
import org.around.ieuri.tourism.application.port.out.persistence.DistrictVisitStorePort
import org.around.ieuri.tourism.application.port.out.persistence.DistrictVisitUpsertPort
import org.around.ieuri.tourism.application.port.out.persistence.MapShareLinkIssuePort
import org.around.ieuri.tourism.application.port.out.persistence.MapShareLinkPort
import org.around.ieuri.tourism.application.port.out.query.DistrictVisitQueryPort
import org.around.ieuri.tourism.application.port.out.query.MapCellDistrictQueryPort
import org.around.ieuri.tourism.application.port.out.query.MapCellQueryPort
import org.around.ieuri.tourism.application.query.MapShareLinkView
import org.around.ieuri.tourism.application.query.toView
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.MapCellGrouping
import org.around.ieuri.tourism.domain.vo.ShareToken
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 전국 지도 쓰기 유스케이스 구현(SPEC-0030 R3·R4·R12·R14).
 *
 * **저장하는 것은 시군구 코드뿐이다** — 좌표도, 기록 시각도, 판정 출처도 저장하지 않는다
 * (R5, POL-045). 저장 포트의 시그니처가 그것을 타입으로 강제한다.
 */
@Service
class NationwideMapCommandService(
    private val districtVisitStorePort: DistrictVisitStorePort,
    private val districtVisitUpsertPort: DistrictVisitUpsertPort,
    private val districtVisitQueryPort: DistrictVisitQueryPort,
    private val mapCellQueryPort: MapCellQueryPort,
    private val mapCellDistrictQueryPort: MapCellDistrictQueryPort,
    private val mapShareLinkPort: MapShareLinkPort,
    private val mapShareLinkIssuePort: MapShareLinkIssuePort,
) : NationwideMapCommandUseCase {

    @Transactional
    override fun recordVisits(command: RecordVisitsCommand, locale: DomainLocale): RecordVisitsResult {
        val requested = command.districtCodes.filter { it.isNotBlank() }.distinct()
        if (requested.isEmpty()) {
            return RecordVisitsResult(updatedCells = emptyList(), unknownDistrictCodes = emptyList())
        }

        // 없는 코드를 조용히 버리지 않는다 — 클라이언트가 DISTRICT_NOT_FOUND를 받을 근거다.
        val existing = districtVisitQueryPort.filterExistingDistrictCodes(requested)
        val unknown = requested.filterNot { it in existing }

        val added = districtVisitUpsertPort.record(command.memberId, existing)

        // **이번 요청으로 방문이 된 칸만** 돌려준다. 이미 방문한 칸은 상태가 바뀌지 않았다.
        //
        // 칸마다 mapCell을 부르지 않는다 — 사진 동기화가 한 번에 수십 칸을 방문으로 바꾸면
        // 그 수만큼 질의가 두 배로 나간다. 시군구를 한 번에 읽고 도메인이 접는다.
        val updatedCellIds = mapCellDistrictQueryPort.findCellIdsByDistrictCodes(added)
        val visited = districtVisitQueryPort.findVisitedDistrictCodes(command.memberId)
        val updatedCells = MapCellGrouping.fold(mapCellQueryPort.findCellDistrictsIn(updatedCellIds))
            .map { it.toView(locale, visited) }

        return RecordVisitsResult(updatedCells = updatedCells, unknownDistrictCodes = unknown)
    }

    @Transactional
    override fun removeVisit(command: RemoveVisitCommand, locale: DomainLocale): RemoveVisitResult {
        // 칸에 속한 시군구 전부를 지운다(R4) — 광역시 칸이면 25개가 한 번에 지워진다.
        val rows = mapCellQueryPort.findCellDistricts(command.cellId)
        if (rows.isEmpty()) return RemoveVisitResult(updatedCell = null)

        districtVisitStorePort.remove(command.memberId, rows.map { it.districtCode })

        // 이미 읽은 rows로 접는다 — 조회 유스케이스를 다시 부르면 같은 시군구를 두 번 읽는다.
        val visited = districtVisitQueryPort.findVisitedDistrictCodes(command.memberId)
        return RemoveVisitResult(
            updatedCell = MapCellGrouping.fold(rows).single().toView(locale, visited),
        )
    }

    @Transactional
    override fun createShareLink(memberId: Long): MapShareLinkView {
        val record = mapShareLinkIssuePort.findOrCreate(memberId, ShareToken.generate())
        return MapShareLinkView(token = record.token, createdAt = record.createdAt)
    }

    @Transactional
    override fun revokeShareLink(memberId: Long, token: String): Boolean =
        mapShareLinkPort.revoke(memberId, token)
}
