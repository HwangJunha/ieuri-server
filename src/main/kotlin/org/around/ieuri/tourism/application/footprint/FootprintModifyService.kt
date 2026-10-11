package org.around.ieuri.tourism.application.footprint

import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.tourism.application.footprint.provided.FootprintCommandUseCase
import org.around.ieuri.tourism.application.footprint.provided.dto.MapShareLinkView
import org.around.ieuri.tourism.application.footprint.provided.dto.RecordVisitsCommand
import org.around.ieuri.tourism.application.footprint.provided.dto.RecordVisitsResult
import org.around.ieuri.tourism.application.footprint.provided.dto.RemoveVisitCommand
import org.around.ieuri.tourism.application.footprint.provided.dto.RemoveVisitResult
import org.around.ieuri.tourism.application.footprint.required.FootprintRepository
import org.around.ieuri.tourism.domain.footprint.ShareToken
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/** 발자취 쓰기(SPEC-0030). 무엇을 남기고 지울지는 발자취가 정하고, 서비스는 바뀐 것만 쓴다. 칸으로 보여 주는 것은 조회 쪽이다. */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX)
class FootprintModifyService(
    private val footprintRepository: FootprintRepository,
    private val policy: FootprintPolicy,
) : FootprintCommandUseCase {

    /** 없는 코드는 버리지 않고 DISTRICT_NOT_FOUND의 근거로 돌려준다. */
    override fun recordVisits(command: RecordVisitsCommand, memberId: Long): RecordVisitsResult {
        val footprint = footprintRepository.findByMemberId(memberId)

        val visit = footprint.visit(command.districtCodes, footprintRepository.filterExistingDistrictCodes(command.districtCodes))

        footprintRepository.addVisits(memberId, visit.addedDistrictCodes)
        return RecordVisitsResult(addedDistrictCodes = visit.addedDistrictCodes.toList(), unknownDistrictCodes = visit.unknownDistrictCodes)
    }

    /** 칸에 속한 시군구 전부를 해제한다(R4). 없는 칸이면 갱신된 칸이 null이다. */
    override fun removeVisit(command: RemoveVisitCommand, memberId: Long): RemoveVisitResult {
        val districtCodes = footprintRepository.findDistrictCodesOfCell(command.cellId)
        if (districtCodes.isEmpty()) return RemoveVisitResult(updatedCellId = null)
        val footprint = footprintRepository.findByMemberId(memberId)

        val unvisit = footprint.unvisit(districtCodes)

        footprintRepository.removeVisits(memberId, unvisit.removedDistrictCodes)
        return RemoveVisitResult(updatedCellId = command.cellId)
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
