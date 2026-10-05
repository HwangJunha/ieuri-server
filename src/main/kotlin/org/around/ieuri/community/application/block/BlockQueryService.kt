package org.around.ieuri.community.application.block

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.community.application.block.provided.BlockQueryUseCase
import org.around.ieuri.community.application.shared.CommunityPolicy
import org.around.ieuri.community.application.shared.clampPageSize
import org.around.ieuri.community.application.shared.dto.BlockedUserView
import org.around.ieuri.community.application.shared.dto.BlockedUsersQuery
import org.around.ieuri.community.application.shared.required.BlockedUsersSearch
import org.around.ieuri.community.application.shared.required.CommunityQueryPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX, readOnly = true)
class BlockQueryService(
    private val communityQueryPort: CommunityQueryPort,
    private val policy: CommunityPolicy,
) : BlockQueryUseCase {

    override fun blockedUsers(query: BlockedUsersQuery, memberId: Long): CursorPage<BlockedUserView> = communityQueryPort.findBlockedUsers(
        BlockedUsersSearch(memberId = memberId, first = policy.clampPageSize(query.first), after = query.after),
    )

    /** 양방향 차단 판정(POL-041). */
    override fun isBlockedBetween(memberId: Long, counterpartId: Long): Boolean = counterpartId in communityQueryPort.findBlockedMemberIds(memberId)
}
