package org.around.ieuri.community.application.block.provided

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.community.application.shared.dto.BlockedUserView
import org.around.ieuri.community.application.shared.dto.BlockedUsersQuery

/** 차단 조회. 다른 context(알림·지도)도 차단 판정에 쓴다. */
interface BlockQueryUseCase {
    /** 내가 건 차단만. 나를 차단한 사람은 뜨지 않는다. */
    fun blockedUsers(query: BlockedUsersQuery, memberId: Long): CursorPage<BlockedUserView>

    /** 두 회원이 어느 방향으로든 차단 관계인지(POL-041). */
    fun isBlockedBetween(memberId: Long, counterpartId: Long): Boolean
}
