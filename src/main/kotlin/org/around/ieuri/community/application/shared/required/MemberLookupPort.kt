package org.around.ieuri.community.application.shared.required

import org.around.ieuri.community.application.shared.required.dto.MemberSummaryView

/** 회원 조회(auth). */
interface MemberLookupPort {
    /** 글·댓글에 실을 최소 작성자 정보. 존재하지 않으면 null이며 그것이 존재 확인이다. */
    fun findAuthor(memberId: Long): MemberSummaryView?

    /** 여러 작성자를 한 번에. 탈퇴 등으로 없는 회원은 결과에 키가 없다. */
    fun findAuthors(memberIds: List<Long>): Map<Long, MemberSummaryView>
}
