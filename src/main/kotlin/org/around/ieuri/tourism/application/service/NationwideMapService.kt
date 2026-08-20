package org.around.ieuri.tourism.application.service

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.keysetPageOf
import org.around.ieuri.tourism.application.exception.InvalidCursorException
import org.around.ieuri.tourism.application.exception.MapCellNotFoundException
import org.around.ieuri.tourism.application.exception.ShareLinkBlockedException
import org.around.ieuri.tourism.application.exception.ShareLinkNotFoundException
import org.around.ieuri.tourism.application.exception.TooManyCoordinatesException
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
import org.around.ieuri.tourism.application.query.CellSpotsQuery
import org.around.ieuri.tourism.application.query.MapCellView
import org.around.ieuri.tourism.application.query.ResolveDistrictsResult
import org.around.ieuri.tourism.application.query.SharedMapView
import org.around.ieuri.tourism.application.query.toView
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.LikeCountCursor
import org.around.ieuri.tourism.domain.vo.MapCellGrouping
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 전국 지도 조회 유스케이스 구현(SPEC-0030, CQRS 읽기).
 *
 * **좌표는 이 서비스를 통과할 뿐 어디에도 남지 않는다**(R1, POL-045). 판정 입력으로만 쓰고
 * 결과(시군구 코드)만 밖으로 나간다. 로그에 좌표를 찍지 않으며, `Coordinates.toString()`이
 * 마스킹되어 있어 실수로 문자열에 섞여도 새지 않는다(POL-013).
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
    override fun mapCell(cellCode: String, locale: DomainLocale, memberId: Long?): MapCellView {
        val rows = mapCellQueryPort.findCellDistricts(cellCode)
        if (rows.isEmpty()) throw MapCellNotFoundException()

        val cell = MapCellGrouping.fold(rows).single()
        return cell.toView(locale, visitedDistrictCodes(memberId))
    }

    @Transactional(readOnly = true)
    override fun allCellImages(): Map<String, CellImageView> =
        cellImageQueryPort.findAllRepresentativeImages()

    @Transactional(readOnly = true)
    override fun cellSpots(query: CellSpotsQuery, locale: DomainLocale): CursorPage<CellSpotView> {
        val first = query.first.coerceIn(1, policy.maxCellSpotPageSize)
        val after = query.after?.let {
            try {
                LikeCountCursor.decode(it)
            } catch (e: IllegalArgumentException) {
                throw InvalidCursorException(cause = e)
            }
        }

        val rows = mapCellQueryPort.findCellSpots(
            cellCode = query.cellCode,
            language = locale,
            limit = first + 1,
            after = after,
        )
        return keysetPageOf(rows = rows, first = first, after = query.after) {
            LikeCountCursor(it.likeCount, it.id).encode()
        }
    }

    @Transactional(readOnly = true)
    override fun resolveDistricts(coordinates: List<Coordinates>): ResolveDistrictsResult {
        if (coordinates.size > policy.maxResolveCoordinates) {
            // 개수만 말한다 — 좌표 자체는 메시지에도 담지 않는다(POL-013).
            throw TooManyCoordinatesException(
                "한 번에 판정할 수 있는 좌표는 최대 ${policy.maxResolveCoordinates}건입니다.",
            )
        }
        val resolved = mapCellQueryPort.resolveDistrictCodes(coordinates)

        // 판정 실패는 결과에서 빠질 뿐 오류가 아니다(R2). 포트가 좌표당 최대 1건을 보장하므로
        // (경계에 걸친 좌표도 한 시군구로 정한다) 뺄셈이 음수가 되지 않는다.
        val unresolvedCount = (coordinates.size - resolved.size).coerceAtLeast(0)
        val districtCodes = resolved.distinct().sorted()
        val cellCodes = mapCellDistrictQueryPort.findCellCodesByDistrictCodes(districtCodes)

        return ResolveDistrictsResult(
            districtCodes = districtCodes,
            cellCodes = cellCodes,
            unresolvedCount = unresolvedCount,
        )
    }

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

        val ownerCells = visitedCellCodes(link.memberId)
        val overlapping = viewerMemberId?.let { viewer ->
            val viewerCells = visitedCellCodes(viewer)
            ownerCells.filter { it in viewerCells }
        }

        return SharedMapView(
            ownerNickname = ownerNickname,
            visitedCellCodes = ownerCells,
            overlappingCellCodes = overlapping,
            visitedCount = ownerCells.size,
        )
    }

    /** 비인증이면 빈 집합이다 — 모든 칸이 미방문으로 나온다(R6). */
    private fun visitedDistrictCodes(memberId: Long?): Set<String> =
        memberId?.let { districtVisitQueryPort.findVisitedDistrictCodes(it) } ?: emptySet()

    /**
     * 회원이 방문한 **칸** 코드. 시군구 하나만 방문해도 그 칸은 방문이다.
     *
     * 정렬을 고정한다 — 공유 지도의 목록 순서가 호출마다 달라지면 클라이언트가 비교할 수 없다.
     */
    private fun visitedCellCodes(memberId: Long): List<String> {
        val visited = districtVisitQueryPort.findVisitedDistrictCodes(memberId)
        if (visited.isEmpty()) return emptyList()
        return mapCellDistrictQueryPort.findCellCodesByDistrictCodes(visited)
    }
}
