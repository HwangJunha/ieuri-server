package org.around.ieuri.community.application.block.required

import org.around.ieuri.community.domain.block.Block

/** 차단 저장소. */
interface BlockRepository {
    /** 어느 쪽이 차단했든 true(양방향). */
    fun existsBetween(memberId: Long, otherId: Long): Boolean

    /** 새로 차단했으면 true, 이미 차단 중이었으면 false(멱등). */
    fun add(block: Block): Boolean

    fun remove(blockerId: Long, blockedId: Long): Boolean
}
