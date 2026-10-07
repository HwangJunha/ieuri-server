package org.around.ieuri.community.application.lookup.provided

import org.around.ieuri.community.domain.lookup.MemberSummary

/** 회원(auth) 조회. 차단·대화·신고 서비스와 GraphQL DataLoader가 함께 쓴다. */
interface MemberLookupUseCase {
    /** 존재하지 않으면 null이며 그것이 존재 확인이다. */
    fun member(memberId: Long): MemberSummary?

    /** 여러 회원을 한 번에. 탈퇴 등으로 없는 회원은 결과에 키가 없다. */
    fun members(memberIds: List<Long>): Map<Long, MemberSummary>
}
