package org.around.ieuri.community.application.block.provided

/** 차단·해제. 중복 차단과 미차단 해제는 오류가 아니라 멱등 no-op이다. */
interface BlockCommandUseCase {
    fun block(targetMemberId: Long, memberId: Long)

    fun unblock(targetMemberId: Long, memberId: Long)
}
