package org.around.ieuri.community.application.block.provided.dto

import org.around.ieuri.community.domain.lookup.MemberSummary
import java.time.Instant

/**
 * 차단 목록 항목. [blockedAt]은 노드가 아니라 엣지의 값이며 리졸버가 옮긴다.
 * - [member]가 null이면 탈퇴 등으로 없는 회원이다. 리졸버가 [memberId]로 대체 표기를 채운다.
 */
data class BlockedUserView(
    val memberId: Long,
    val member: MemberSummary?,
    val blockedAt: Instant,
)
