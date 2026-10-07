package org.around.ieuri.community.application.block.provided.dto

import java.time.Instant

/** 차단 목록 항목. 회원 정보는 리졸버가 [memberId]로 채우고, [blockedAt]은 노드가 아니라 엣지의 값이다. */
data class BlockedUserView(
    val memberId: Long,
    val blockedAt: Instant,
)
