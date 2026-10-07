package org.around.ieuri.community.adapter.persistence.query

import org.around.ieuri.common.pagination.CursorEdge
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.community.adapter.persistence.table.BlockTable
import org.around.ieuri.community.application.block.provided.dto.BlockedUserView
import org.around.ieuri.community.application.block.required.BlockQueryPort
import org.around.ieuri.community.application.block.required.dto.BlockedUsersSearch
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Component

/**
 * 차단 읽기 어댑터. 트랜잭션은 호출하는 조회 서비스가 연다.
 * - 차단 상대의 닉네임 등 다른 도메인 소유 정보는 비워 두고 조회 서비스가 채운다.
 */
@Component
class BlockQueryAdapter : BlockQueryPort {

    /** 내가 건 차단만. 나를 차단한 사람은 뜨지 않는다. */
    override fun findBlockedUsers(search: BlockedUsersSearch): CursorPage<BlockedUserView> {
        val cursor = search.after?.let(::decodeCursor)
        val rows = BlockTable.selectAll()
            .where {
                allOf(
                    BlockTable.blockerId eq search.memberId,
                    cursor?.let { keysetBefore(BlockTable.createdAt, BlockTable.id, it) },
                )
            }
            .orderBy(BlockTable.createdAt to SortOrder.DESC, BlockTable.id to SortOrder.DESC)
            .limit(search.first + 1)
            .toList()
        return page(rows, search.first, cursor != null) { row ->
            val blockedAt = row[BlockTable.createdAt].toInstant()
            CursorEdge(
                encodeCursor(blockedAt, row[BlockTable.id]),
                BlockedUserView(memberId = row[BlockTable.blockedId], blockedAt = blockedAt),
            )
        }
    }

    /** 양방향 차단 관계의 상대 회원 id. */
    override fun findBlockedMemberIds(memberId: Long): Set<Long> = BlockTable.select(BlockTable.blockerId, BlockTable.blockedId)
        .where { (BlockTable.blockerId eq memberId) or (BlockTable.blockedId eq memberId) }
        .map { if (it[BlockTable.blockerId] == memberId) it[BlockTable.blockedId] else it[BlockTable.blockerId] }
        .toSet()
}
