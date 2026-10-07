package org.around.ieuri.tourism.application.footprint

import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.tourism.application.footprint.provided.FootprintQueryUseCase
import org.around.ieuri.tourism.application.footprint.provided.dto.CellImageView
import org.around.ieuri.tourism.application.footprint.provided.dto.CellSpotView
import org.around.ieuri.tourism.application.footprint.provided.dto.MapCellView
import org.around.ieuri.tourism.application.footprint.provided.dto.SharedMapView
import org.around.ieuri.tourism.application.footprint.provided.exception.MapCellNotFoundException
import org.around.ieuri.tourism.application.footprint.provided.exception.ShareLinkBlockedException
import org.around.ieuri.tourism.application.footprint.provided.exception.ShareLinkNotFoundException
import org.around.ieuri.tourism.application.footprint.required.CellImageQueryPort
import org.around.ieuri.tourism.application.footprint.required.FootprintQueryPort
import org.around.ieuri.tourism.application.footprint.required.MapCellDistrictQueryPort
import org.around.ieuri.tourism.application.footprint.required.MapCellQueryPort
import org.around.ieuri.tourism.application.footprint.required.MapOwnerLookupPort
import org.around.ieuri.tourism.application.footprint.required.MapViewerBlockPort
import org.around.ieuri.tourism.domain.footprint.MapCellGrouping
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 전국 지도 조회 유스케이스 구현(SPEC-0030). 좌표를 받지 않고 시군구 코드만 다룬다(POL-045). */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX, readOnly = true)
class FootprintQueryService(
    private val mapCellQueryPort: MapCellQueryPort,
    private val mapCellDistrictQueryPort: MapCellDistrictQueryPort,
    private val footprintQueryPort: FootprintQueryPort,
    private val cellImageQueryPort: CellImageQueryPort,
    private val mapOwnerLookupPort: MapOwnerLookupPort,
    private val mapViewerBlockPort: MapViewerBlockPort,
    private val policy: FootprintPolicy,
) : FootprintQueryUseCase {

    override fun mapCells(locale: DomainLocale, memberId: Long?): List<MapCellView> {
        val cells = MapCellGrouping.fold(mapCellQueryPort.findAllCellDistricts())
        val visited = visitedDistrictCodes(memberId)
        return cells.map { it.toView(locale, visited) }
    }

    override fun mapCell(cellId: Long, locale: DomainLocale, memberId: Long?): MapCellView {
        val rows = mapCellQueryPort.findCellDistricts(cellId)
        if (rows.isEmpty()) throw MapCellNotFoundException()

        val cell = MapCellGrouping.fold(rows).single()
        return cell.toView(locale, visitedDistrictCodes(memberId))
    }

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

    override fun allCellImages(): Map<Long, CellImageView> = cellImageQueryPort.findAllRepresentativeImages()

    override fun cellSpots(cellId: Long, first: Int, locale: DomainLocale): List<CellSpotView> = // 상한 초과는 절삭이다(POL-018).
        mapCellQueryPort.findCellSpots(
            cellId = cellId,
            language = locale,
            limit = first.coerceIn(1, policy.maxCellSpotPageSize),
        )

    override fun sharedMap(token: String, viewerMemberId: Long?): SharedMapView {
        val ownerId = footprintQueryPort.findSharerId(token) ?: throw ShareLinkNotFoundException()

        // 차단은 양방향이라 누가 걸었는지는 묻지 않는다(POL-041).
        if (viewerMemberId != null &&
            viewerMemberId != ownerId &&
            mapViewerBlockPort.isBlockedBetween(viewerMemberId, ownerId)
        ) {
            throw ShareLinkBlockedException()
        }

        val ownerNickname = mapOwnerLookupPort.findNickname(ownerId)
            ?: throw ShareLinkNotFoundException()

        val ownerCells = visitedCellIds(ownerId)
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

    /** 비인증이면 빈 집합이다 — 모든 칸이 미방문으로 나온다. */
    private fun visitedDistrictCodes(memberId: Long?): Set<String> = memberId?.let { footprintQueryPort.findVisitedDistrictCodes(it) } ?: emptySet()

    /** 회원이 방문한 칸 id(오름차순). 시군구 하나만 방문해도 그 칸은 방문이다. */
    private fun visitedCellIds(memberId: Long): List<Long> {
        val visited = footprintQueryPort.findVisitedDistrictCodes(memberId)
        if (visited.isEmpty()) return emptyList()
        return mapCellDistrictQueryPort.findCellIdsByDistrictCodes(visited)
    }
}
