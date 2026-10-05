package org.around.ieuri.community.application.withdrawal

import java.time.Instant

/**
 * 탈퇴 회원 데이터 정리의 한 단계. 애그리게이트 종류마다 하나이며 단계마다 새 트랜잭션이다(REQUIRES_NEW).
 * - 남에게 보이는 콘텐츠는 삭제 표시, 본인만의 데이터(좋아요·숨김·차단)는 물리 삭제하고 카운터를 보정한다.
 */
interface MemberContentEraser {
    /** 바뀐 행 수(로그용). 한 번의 탈퇴는 모든 단계에 같은 [deletedAt]을 남긴다. */
    fun eraseMemberContent(memberId: Long, deletedAt: Instant): Int
}
