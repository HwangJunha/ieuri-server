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

/**
 * 지도 관계 use case 구현(SPEC-0030 확장).
 *
 * 공유 링크가 단방향 스냅샷이라 "여러 명과 겹치는 곳"을 볼 수 없었다. 관계를 저장해
 * 그것을 양방향으로 만든다 — 링크 자체는 그대로이며 관계는 그 위에 얹힌다.
 */
@Service
class MapRelationService(
    private val mapRelationPort: MapRelationPort,
    private val mapRelationQueryPort: MapRelationQueryPort,
    private val mapShareLinkPort: MapShareLinkPort,
    private val mapOwnerLookupPort: MapOwnerLookupPort,
    private val mapViewerBlockPort: MapViewerBlockPort,
    private val policy: NationwideMapPolicy,
) : MapRelationQueryUseCase, MapRelationCommandUseCase {

    @Transactional
    override fun createRelation(memberId: Long, shareUrl: String): Long {
        val token = ShareToken.extractFrom(shareUrl)
        val link = mapShareLinkPort.findActiveByToken(token) ?: throw ShareLinkNotFoundException()

        // 자기 링크를 여는 것은 정상이지만 거기서 등록하면 목록에 자기가 나타난다.
        if (link.memberId == memberId) throw CannotRelateSelfException()

        // 차단은 **등록 시점에만** 본다(§5). 나중에 차단해도 관계는 남으며 각자 지운다 —
        // 자동으로 빼면 상대가 내 목록을 바꾸는 셈이라 삭제 비대칭 원칙과 충돌한다.
        if (mapViewerBlockPort.isBlockedBetween(memberId, link.memberId)) {
            throw ShareLinkBlockedException()
        }

        mapRelationPort.relate(memberId, link.memberId)
        return link.memberId
    }

    @Transactional
    override fun removeRelation(memberId: Long, counterpartId: Long): Boolean =
        mapRelationPort.removeFromMyList(memberId, counterpartId)

    /**
     * 겹침은 포트가 한 질의로 계산해 오고, 닉네임만 다른 도메인에 **벌크로** 묻는다 —
     * 건별로 물으면 한 페이지에 왕복이 20번이 된다.
     *
     * 닉네임을 얻지 못한 상대(탈퇴 등)는 목록에서 뺀다. 이름 없는 행을 그리면 화면이 깨지고,
     * 탈퇴자는 정리 리스너가 곧 지운다.
     *
     * **페이지 경계는 거르기 전에 정한다.** 걸러낸 뒤의 개수로 판정하면 한 페이지에 탈퇴자가
     * 한 명만 섞여도 `first + 1`건이 `first`건으로 줄어 다음 페이지가 없다고 나가고,
     * 그 뒤의 관계는 클라이언트가 영영 볼 수 없게 된다. 커서도 같은 이유로 살아남은 마지막
     * 행이 아니라 **읽어 온 마지막 행**에서 뽑는다.
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

        // endCursor는 걸러진 뒤의 마지막이 아니라 읽어 온 마지막이어야 한다 — 페이지 끝의
        // 탈퇴자가 잘려 나가면 그 위치를 잃고 다음 페이지가 이미 본 행부터 다시 시작한다.
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
