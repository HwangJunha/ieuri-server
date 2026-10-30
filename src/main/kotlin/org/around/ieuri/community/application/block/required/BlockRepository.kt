package org.around.ieuri.community.application.block.required

import org.around.ieuri.community.domain.block.Block

/** 차단 저장소. */
interface BlockRepository {
    /** 새로 차단했으면 true, 이미 차단 중이었으면 false(멱등). */
    fun add(block: Block): Boolean

    fun remove(blockerId: Long, blockedId: Long): Boolean

    /** 탈퇴 정리: 회원이 걸었거나 회원을 향한 차단을 모두 지운다. */
    fun removeInvolving(memberId: Long): Int
}
