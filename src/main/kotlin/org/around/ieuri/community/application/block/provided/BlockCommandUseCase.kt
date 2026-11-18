package org.around.ieuri.community.application.block.provided

import java.time.Instant

/** 차단·해제. 중복 차단과 미차단 해제는 오류가 아니라 멱등 no-op이다. */
interface BlockCommandUseCase {
    fun block(targetMemberId: Long, memberId: Long)

    fun unblock(targetMemberId: Long, memberId: Long)

    /** 탈퇴 정리. 탈퇴 커밋 뒤 리스너가 부르며 새 트랜잭션이다. 한 번의 탈퇴는 모든 애그리게이트에 같은 [deletedAt]을 남긴다. 바뀐 행 수(로그용). */
    fun eraseMemberContent(memberId: Long, deletedAt: Instant): Int
}
