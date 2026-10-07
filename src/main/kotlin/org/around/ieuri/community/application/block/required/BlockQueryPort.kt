package org.around.ieuri.community.application.block.required

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.community.application.block.provided.dto.BlockedUserView
import org.around.ieuri.community.application.block.required.dto.BlockedUsersSearch

/** 차단 읽기 포트(CQRS 읽기). */
interface BlockQueryPort {
    fun findBlockedUsers(search: BlockedUsersSearch): CursorPage<BlockedUserView>

    /** [memberId]와 차단 관계에 있는 상대의 id. 내가 차단한 쪽과 나를 차단한 쪽을 모두 담는다. */
    fun findBlockedMemberIds(memberId: Long): Set<Long>
}
