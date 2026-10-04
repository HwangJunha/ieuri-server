package org.around.ieuri.community.application.block.required

import org.around.ieuri.community.domain.block.Block

/** 차단 쓰기. 양방향 조회는 읽기 쪽이 담당한다. */
interface BlockRepository {
    /** 새로 차단했으면 true, 이미 차단 중이었으면 false(멱등). */
    fun add(block: Block): Boolean

    fun remove(blockerId: Long, blockedId: Long): Boolean
}
