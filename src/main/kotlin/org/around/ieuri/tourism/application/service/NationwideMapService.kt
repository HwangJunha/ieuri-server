package org.around.ieuri.tourism.application.service

import org.around.ieuri.tourism.application.exception.MapCellNotFoundException
import org.around.ieuri.tourism.application.exception.ShareLinkBlockedException
import org.around.ieuri.tourism.application.exception.ShareLinkNotFoundException
import org.around.ieuri.tourism.application.policy.NationwideMapPolicy
import org.around.ieuri.tourism.application.port.`in`.query.NationwideMapUseCase
import org.around.ieuri.tourism.application.port.out.client.MapOwnerLookupPort
import org.around.ieuri.tourism.application.port.out.client.MapViewerBlockPort
import org.around.ieuri.tourism.application.port.out.persistence.MapShareLinkPort
import org.around.ieuri.tourism.application.port.out.query.CellImageQueryPort
import org.around.ieuri.tourism.application.port.out.query.DistrictVisitQueryPort
import org.around.ieuri.tourism.application.port.out.query.MapCellDistrictQueryPort
import org.around.ieuri.tourism.application.port.out.query.MapCellQueryPort
import org.around.ieuri.tourism.application.query.CellImageView
import org.around.ieuri.tourism.application.query.CellSpotView
import org.around.ieuri.tourism.application.query.MapCellView
import org.around.ieuri.tourism.application.query.SharedMapView
import org.around.ieuri.tourism.application.query.toView
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.MapCellGrouping
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 전국 지도 조회 유스케이스 구현(SPEC-0030, CQRS 읽기).
 *
 * **좌표를 아예 받지 않는다**(POL-045). 좌표→시군구 판정은 클라이언트가 로컬 경계 데이터로
 * 하고, 서버는 그 결과(시군구 코드)만 받는다 — 저장하지 않겠다는 약속이 아니라 구조로
 * 보장되는 형태다.
 */
@Service
class NationwideMapService(
    private val mapCellQueryPort: MapCellQueryPort,
    private val mapCellDistrictQueryPort: MapCellDistrictQueryPort,
    private val districtVisitQueryPort: DistrictVisitQueryPort,
    private val cellImageQueryPort: CellImageQueryPort,
    private val mapShareLinkPort: MapShareLinkPort,
    private val mapOwnerLookupPort: MapOwnerLookupPort,
    private val mapViewerBlockPort: MapViewerBlockPort,
    private val policy: NationwideMapPolicy,
) : NationwideMapUseCase {

    @Transactional(readOnly = true)
    override fun mapCells(locale: DomainLocale, memberId: Long?): List<MapCellView> {
        val cells = MapCellGrouping.fold(mapCellQueryPort.findAllCellDistricts())
        val visited = visitedDistrictCodes(memberId)
        return cells.map { it.toView(locale, visited) }
    }

    @Transactional(readOnly = true)
    override fun mapCell(cellId: Long, locale: DomainLocale, memberId: Long?): MapCellView {
        val rows = mapCellQueryPort.findCellDistricts(cellId)
        if (rows.isEmpty()) throw MapCellNotFoundException()

        val cell = MapCellGrouping.fold(rows).single()
        return cell.toView(locale, visitedDistrictCodes(memberId))
    }

    @Transactional(readOnly = true)
    override fun mapCellsByIds(
        cellIds: Collection<Long>,
        locale: DomainLocale,
        memberId: Long?,
    ): List<MapCellView> {
        if (cellIds.isEmpty()) return emptyList()

        val cells = MapCellGrouping.fold(mapCellQueryPort.findCellDistrictsIn(cellIds))
        val visited = visitedDistrictCodes(memberId)
        val byId = cells.associateBy { it.id }
        return cellIds.mapNotNull { byId[it] }.map { it.toView(locale, visited) }
    }

    @Transactional(readOnly = true)
    override fun allCellImages(): Map<Long, CellImageView> = cellImageQueryPort.findAllRepresentativeImages()

    @Transactional(readOnly = true)
    override fun cellSpots(cellId: Long, first: Int, locale: DomainLocale): List<CellSpotView> = // 상한 초과는 오류가 아니라 절삭이다(POL-018).
        mapCellQueryPort.findCellSpots(
            cellId = cellId,
            language = locale,
            limit = first.coerceIn(1, policy.maxCellSpotPageSize),
        )

    @Transactional(readOnly = true)
    override fun sharedMap(token: String, viewerMemberId: Long?): SharedMapView {
        val link = mapShareLinkPort.findActiveByToken(token) ?: throw ShareLinkNotFoundException()

        // 차단은 community의 소유다 — tourism이 그 테이블을 직접 읽지 않고 포트로 묻는다.
        // 양방향이라 누가 걸었는지는 묻지 않는다(POL-041).
        if (viewerMemberId != null &&
            viewerMemberId != link.memberId &&
            mapViewerBlockPort.isBlockedBetween(viewerMemberId, link.memberId)
        ) {
            throw ShareLinkBlockedException()
        }

        val ownerNickname = mapOwnerLookupPort.findNickname(link.memberId)
            ?: throw ShareLinkNotFoundException()

        val ownerCells = visitedCellIds(link.memberId)
        val overlapping = viewerMemberId?.let { viewer ->
            val viewerCells = visitedCellIds(viewer)
            ownerCells.filter { it in viewerCells }
        }

        return SharedMapView(
            ownerNickname = ownerNickname,
            visitedCellIds = ownerCells,
            overlappingCellIds = overlapping,
            visitedCount = ownerCells.size,
        )
    }

    /** 비인증이면 빈 집합이다 — 모든 칸이 미방문으로 나온다(R6). */
    private fun visitedDistrictCodes(memberId: Long?): Set<String> = memberId?.let { districtVisitQueryPort.findVisitedDistrictCodes(it) } ?: emptySet()

    /**
     * 회원이 방문한 **칸** id. 시군구 하나만 방문해도 그 칸은 방문이다.
     *
     * 정렬을 고정한다 — 공유 지도의 목록 순서가 호출마다 달라지면 클라이언트가 비교할 수 없다.
     */
    private fun visitedCellIds(memberId: Long): List<Long> {
        val visited = districtVisitQueryPort.findVisitedDistrictCodes(memberId)
        if (visited.isEmpty()) return emptyList()
        return mapCellDistrictQueryPort.findCellIdsByDistrictCodes(visited)
    }
}
