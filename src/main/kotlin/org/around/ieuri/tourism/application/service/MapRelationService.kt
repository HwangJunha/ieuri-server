package org.around.ieuri.tourism.application.service

import org.around.ieuri.common.pagination.CursorEdge
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.CursorPageInfo
import org.around.ieuri.tourism.application.exception.CannotRelateSelfException
import org.around.ieuri.tourism.application.exception.InvalidCursorException
import org.around.ieuri.tourism.application.exception.ShareLinkBlockedException
import org.around.ieuri.tourism.application.exception.ShareLinkNotFoundException
import org.around.ieuri.tourism.application.policy.NationwideMapPolicy
import org.around.ieuri.tourism.application.port.`in`.command.MapRelationCommandUseCase
import org.around.ieuri.tourism.application.port.`in`.query.MapRelationQueryUseCase
import org.around.ieuri.tourism.application.port.out.client.MapOwnerLookupPort
import org.around.ieuri.tourism.application.port.out.client.MapViewerBlockPort
import org.around.ieuri.tourism.application.port.out.persistence.MapRelationPort
import org.around.ieuri.tourism.application.port.out.persistence.MapShareLinkPort
import org.around.ieuri.tourism.application.port.out.query.MapRelationQueryPort
import org.around.ieuri.tourism.application.query.MapRelationView
import org.around.ieuri.tourism.application.query.MapRelationsQuery
import org.around.ieuri.tourism.domain.vo.RelationCursor
import org.around.ieuri.tourism.domain.vo.ShareToken
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 지도 관계 use case 구현(SPEC-0030 확장). 공유 링크 위에 양방향 관계를 얹는다. */
@Service
class MapRelationService(
    private val mapRelationPort: MapRelationPort,
    private val mapRelationQueryPort: MapRelationQueryPort,
    private val mapShareLinkPort: MapShareLinkPort,
    private val mapOwnerLookupPort: MapOwnerLookupPort,
    private val mapViewerBlockPort: MapViewerBlockPort,
    private val policy: NationwideMapPolicy,
) : MapRelationQueryUseCase,
    MapRelationCommandUseCase {

    @Transactional
    override fun createRelation(memberId: Long, shareUrl: String): Long {
        val token = ShareToken.extractFrom(shareUrl)
        val link = mapShareLinkPort.findActiveByToken(token) ?: throw ShareLinkNotFoundException()

        if (link.memberId == memberId) throw CannotRelateSelfException()

        // 차단은 등록 시점에만 본다(§5). 나중에 차단해도 관계는 남는다.
        if (mapViewerBlockPort.isBlockedBetween(memberId, link.memberId)) {
            throw ShareLinkBlockedException()
        }

        mapRelationPort.relate(memberId, link.memberId)
        return link.memberId
    }

    @Transactional
    override fun removeRelation(memberId: Long, counterpartId: Long): Boolean = mapRelationPort.removeFromMyList(memberId, counterpartId)

    /**
     * 겹침은 포트가 계산하고, 닉네임만 다른 도메인에 벌크로 묻는다.
     * - 닉네임을 얻지 못한 상대(탈퇴 등)는 목록에서 뺀다 — 한 페이지가 `first`보다 적을 수 있다.
     * - 페이지 경계(hasNextPage·endCursor)는 거르기 전의 읽어 온 행으로 정한다.
     */
    @Transactional(readOnly = true)
    override fun mapRelations(query: MapRelationsQuery, memberId: Long): CursorPage<MapRelationView> {
        val first = query.first.coerceIn(1, policy.maxRelationPageSize)
        val after = query.after?.let {
            try {
                RelationCursor.decode(it)
            } catch (e: IllegalArgumentException) {
                throw InvalidCursorException(cause = e)
            }
        }

        val fetched = mapRelationQueryPort.findRelations(memberId, first + 1, after)
        val hasNextPage = fetched.size > first
        val page = fetched.take(first)

        val nicknames = mapOwnerLookupPort.findNicknames(page.map { it.counterpartId })
        val edges = page.mapNotNull { overlap ->
            nicknames[overlap.counterpartId]?.let { nickname ->
                CursorEdge(
                    cursor = RelationCursor(overlap.createdAt, overlap.counterpartId).encode(),
                    node = MapRelationView(
                        memberId = overlap.counterpartId,
                        nickname = nickname,
                        overlappingCount = overlap.overlappingCellIds.size,
                        overlappingCellIds = overlap.overlappingCellIds,
                        theirVisitedCount = overlap.theirVisitedCount,
                        createdAt = overlap.createdAt,
                    ),
                )
            }
        }

        // endCursor는 걸러진 뒤가 아니라 읽어 온 마지막 행에서 뽑는다.
        val lastCursor = page.lastOrNull()
            ?.let { RelationCursor(it.createdAt, it.counterpartId).encode() }

        return CursorPage(
            edges = edges,
            pageInfo = CursorPageInfo(
                hasNextPage = hasNextPage,
                hasPreviousPage = query.after != null,
                startCursor = edges.firstOrNull()?.cursor,
                endCursor = lastCursor,
            ),
            totalCount = null,
        )
    }
}
