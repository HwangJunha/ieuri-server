package org.around.ieuri.community.application.block

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.map
import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.community.application.block.provided.BlockQueryUseCase
import org.around.ieuri.community.application.block.provided.dto.BlockedUserView
import org.around.ieuri.community.application.block.provided.dto.BlockedUsersQuery
import org.around.ieuri.community.application.block.required.BlockQueryPort
import org.around.ieuri.community.application.block.required.dto.BlockedUsersSearch
import org.around.ieuri.community.application.lookup.provided.MemberLookupUseCase
import org.around.ieuri.community.application.shared.PagePolicy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX, readOnly = true)
class BlockQueryService(
    private val blockQueryPort: BlockQueryPort,
    private val memberLookup: MemberLookupUseCase,
    private val pagePolicy: PagePolicy,
) : BlockQueryUseCase {

    /** 회원 정보가 목록의 본체라 지연시키지 않고 페이지 단위로 한 번에 채운다. */
    override fun blockedUsers(query: BlockedUsersQuery, memberId: Long): CursorPage<BlockedUserView> {
        val page = blockQueryPort.findBlockedUsers(
            BlockedUsersSearch(memberId = memberId, first = pagePolicy.clamp(query.first), after = query.after),
        )
        val members = memberLookup.members(page.edges.map { it.node.memberId })
        return page.map { it.copy(member = members[it.memberId]) }
    }

    /** 양방향 차단 판정(POL-041). */
    override fun isBlockedBetween(memberId: Long, counterpartId: Long): Boolean = counterpartId in blockedMemberIds(memberId)

    override fun blockedMemberIds(memberId: Long): Set<Long> = blockQueryPort.findBlockedMemberIds(memberId)
}
