package org.around.ieuri.community.application.block

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.community.application.block.provided.BlockQueryUseCase
import org.around.ieuri.community.application.block.provided.dto.BlockedUserView
import org.around.ieuri.community.application.block.provided.dto.BlockedUsersQuery
import org.around.ieuri.community.application.block.required.BlockQueryPort
import org.around.ieuri.community.application.block.required.dto.BlockedUsersSearch
import org.around.ieuri.community.application.shared.PagePolicy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class BlockQueryService(
    private val blockQueryPort: BlockQueryPort,
    private val pagePolicy: PagePolicy,
) : BlockQueryUseCase {

    override fun blockedUsers(query: BlockedUsersQuery, memberId: Long): CursorPage<BlockedUserView> = blockQueryPort.findBlockedUsers(
        BlockedUsersSearch(memberId = memberId, first = pagePolicy.clamp(query.first), after = query.after),
    )

    /** 양방향 차단 판정(POL-041). */
    override fun isBlockedBetween(memberId: Long, counterpartId: Long): Boolean = counterpartId in blockedMemberIds(memberId)

    override fun blockedMemberIds(memberId: Long): Set<Long> = blockQueryPort.findBlockedMemberIds(memberId)
}
