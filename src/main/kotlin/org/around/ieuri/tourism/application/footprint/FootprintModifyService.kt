package org.around.ieuri.tourism.application.footprint

import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.tourism.application.footprint.provided.FootprintCommandUseCase
import org.around.ieuri.tourism.application.footprint.provided.dto.RecordVisitsCommand
import org.around.ieuri.tourism.application.footprint.provided.dto.RecordVisitsResult
import org.around.ieuri.tourism.application.footprint.provided.dto.RemoveVisitCommand
import org.around.ieuri.tourism.application.footprint.provided.dto.RemoveVisitResult
import org.around.ieuri.tourism.application.footprint.required.FootprintQueryPort
import org.around.ieuri.tourism.application.footprint.required.FootprintRepository
import org.around.ieuri.tourism.application.shared.NationwideMapPolicy
import org.around.ieuri.tourism.application.shared.provided.dto.MapShareLinkView
import org.around.ieuri.tourism.application.shared.provided.dto.toView
import org.around.ieuri.tourism.application.shared.required.MapCellDistrictQueryPort
import org.around.ieuri.tourism.application.shared.required.MapCellQueryPort
import org.around.ieuri.tourism.domain.footprint.ShareToken
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.MapCellGrouping
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/** 발자취 쓰기(SPEC-0030). 무엇을 남기고 지울지는 발자취가 정하고, 서비스는 바뀐 것만 쓴 뒤 칸으로 보여 준다. */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX)
class FootprintModifyService(
    private val footprintRepository: FootprintRepository,
    private val footprintQueryPort: FootprintQueryPort,
    private val mapCellQueryPort: MapCellQueryPort,
    private val mapCellDistrictQueryPort: MapCellDistrictQueryPort,
    private val policy: NationwideMapPolicy,
) : FootprintCommandUseCase {

    /** 없는 코드는 버리지 않고 DISTRICT_NOT_FOUND의 근거로 돌려준다. 이번 요청으로 방문이 된 칸만 돌려준다. */
    override fun recordVisits(command: RecordVisitsCommand, locale: DomainLocale): RecordVisitsResult {
        val footprint = footprintRepository.findByMemberId(command.memberId)

        val visit = footprint.visit(command.districtCodes, footprintQueryPort.filterExistingDistrictCodes(command.districtCodes))

        footprintRepository.addVisits(command.memberId, visit.addedDistrictCodes)

        val updatedCellIds = mapCellDistrictQueryPort.findCellIdsByDistrictCodes(visit.addedDistrictCodes)
        val updatedCells = MapCellGrouping.fold(mapCellQueryPort.findCellDistrictsIn(updatedCellIds))
            .map { it.toView(locale, visit.footprint.visitedDistrictCodes) }
        return RecordVisitsResult(updatedCells = updatedCells, unknownDistrictCodes = visit.unknownDistrictCodes)
    }

    /** 칸에 속한 시군구 전부를 해제한다(R4). 없는 칸이면 갱신된 칸이 null이다. */
    override fun removeVisit(command: RemoveVisitCommand, locale: DomainLocale): RemoveVisitResult {
        val rows = mapCellQueryPort.findCellDistricts(command.cellId)
        if (rows.isEmpty()) return RemoveVisitResult(updatedCell = null)
        val footprint = footprintRepository.findByMemberId(command.memberId)

        val unvisit = footprint.unvisit(rows.map { it.districtCode })

        footprintRepository.removeVisits(command.memberId, unvisit.removedDistrictCodes)
        return RemoveVisitResult(updatedCell = MapCellGrouping.fold(rows).single().toView(locale, unvisit.footprint.visitedDistrictCodes))
    }

    /** 토큰을 설정된 앞부분에 붙여 완성한다 — 클라이언트는 조립하지 않는다. */
    override fun createShareLink(memberId: Long): MapShareLinkView {
        val footprint = footprintRepository.findByMemberId(memberId)

        val link = footprintRepository.saveShareLink(memberId, footprint.share())

        return MapShareLinkView(shareUrl = "${policy.shareUrlBase}/${link.token}", createdAt = requireNotNull(link.issuedAt))
    }

    /** 발급 때 내보낸 URL에서 토큰을 꺼낸다. URL 형태는 검사하지 않는다 — 엉뚱한 값이면 폐기 대상이 없어 false다. */
    override fun revokeShareLink(memberId: Long, shareUrl: String): Boolean {
        val footprint = footprintRepository.findByMemberId(memberId)

        val revoked = footprint.revokeShare(ShareToken.extractFrom(shareUrl)) ?: return false

        return footprintRepository.revokeShareLink(memberId, revoked)
    }

    @Transactional(transactionManager = ExposedConfig.EXPOSED_TX, propagation = Propagation.REQUIRES_NEW)
    override fun eraseMemberContent(memberId: Long): Int = footprintRepository.deleteAll(memberId)
}
