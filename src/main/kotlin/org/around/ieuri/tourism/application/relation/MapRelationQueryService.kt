package org.around.ieuri.tourism.application.relation

import org.around.ieuri.common.pagination.CursorEdge
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.tourism.application.footprint.required.MapOwnerLookupPort
import org.around.ieuri.tourism.application.relation.provided.MapRelationQueryUseCase
import org.around.ieuri.tourism.application.relation.provided.dto.MapRelationView
import org.around.ieuri.tourism.application.relation.provided.dto.MapRelationsQuery
import org.around.ieuri.tourism.application.relation.required.MapRelationQueryPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 지도 관계 목록(SPEC-0030 확장). 겹침은 조회 시점에 다시 계산한다. */
@Service
@Transactional(readOnly = true)
class MapRelationQueryService(
    private val mapRelationQueryPort: MapRelationQueryPort,
    private val mapOwnerLookupPort: MapOwnerLookupPort,
    private val policy: MapRelationPolicy,
) : MapRelationQueryUseCase {

    /**
     * 겹침은 포트가 계산하고, 닉네임만 다른 도메인에 벌크로 묻는다.
     * - 닉네임을 얻지 못한 상대(탈퇴 등)는 목록에서 뺀다 — 한 페이지가 `first`보다 적을 수 있다.
     * - 페이지 경계(hasNextPage·endCursor)는 거르기 전의 읽어 온 행으로 정한다.
     */
    override fun mapRelations(query: MapRelationsQuery, memberId: Long): CursorPage<MapRelationView> {
        val first = query.first.coerceIn(1, policy.maxPageSize)
        val page = mapRelationQueryPort.findRelations(memberId, first, query.after)

        val nicknames = mapOwnerLookupPort.findNicknames(page.edges.map { it.node.counterpartId })
        val edges = page.edges.mapNotNull { edge ->
            val overlap = edge.node
            nicknames[overlap.counterpartId]?.let { nickname ->
                CursorEdge(
                    cursor = edge.cursor,
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

        // hasNextPage·endCursor는 거르기 전 그대로 두고, startCursor만 남은 엣지에서 다시 뽑는다.
        return CursorPage(
            edges = edges,
            pageInfo = page.pageInfo.copy(startCursor = edges.firstOrNull()?.cursor),
            totalCount = null,
        )
    }
}
