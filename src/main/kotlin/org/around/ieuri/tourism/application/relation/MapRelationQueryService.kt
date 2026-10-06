package org.around.ieuri.tourism.application.relation

import org.around.ieuri.common.pagination.CursorEdge
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.CursorPageInfo
import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.tourism.application.relation.provided.MapRelationQueryUseCase
import org.around.ieuri.tourism.application.relation.provided.dto.MapRelationView
import org.around.ieuri.tourism.application.relation.provided.dto.MapRelationsQuery
import org.around.ieuri.tourism.application.relation.required.MapRelationQueryPort
import org.around.ieuri.tourism.application.shared.NationwideMapPolicy
import org.around.ieuri.tourism.application.shared.provided.exception.InvalidCursorException
import org.around.ieuri.tourism.application.shared.required.MapOwnerLookupPort
import org.around.ieuri.tourism.domain.shared.RelationCursor
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 지도 관계 목록(SPEC-0030 확장). 겹침은 조회 시점에 다시 계산한다. */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX, readOnly = true)
class MapRelationQueryService(
    private val mapRelationQueryPort: MapRelationQueryPort,
    private val mapOwnerLookupPort: MapOwnerLookupPort,
    private val policy: NationwideMapPolicy,
) : MapRelationQueryUseCase {

    /**
     * 겹침은 포트가 계산하고, 닉네임만 다른 도메인에 벌크로 묻는다.
     * - 닉네임을 얻지 못한 상대(탈퇴 등)는 목록에서 뺀다 — 한 페이지가 `first`보다 적을 수 있다.
     * - 페이지 경계(hasNextPage·endCursor)는 거르기 전의 읽어 온 행으로 정한다.
     */
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
        val lastCursor = page.lastOrNull()?.let { RelationCursor(it.createdAt, it.counterpartId).encode() }

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
