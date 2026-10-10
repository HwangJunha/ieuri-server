package org.around.ieuri.auth.application.port.out.query

import org.around.ieuri.auth.application.query.MeView
import org.around.ieuri.auth.application.query.MemberDetailView
import org.around.ieuri.auth.application.query.MemberPublicProfileView
import org.around.ieuri.auth.application.query.MemberSummaryView
import org.around.ieuri.auth.application.query.MembersQuery
import org.around.ieuri.common.pagination.CursorPage

/** 회원 읽기 포트. read DTO를 직접 반환하며, 평문 이메일이 필요한 조회(me/단건)는 구현이 복호화한다. */
interface MemberQueryPort {
    fun findMe(memberId: Long): MeView?

    /** 없는 회원은 거짓이다. */
    fun isEmailVerified(memberId: Long): Boolean

    fun findMemberDetail(memberId: Long): MemberDetailView?

    fun findMembers(query: MembersQuery): CursorPage<MemberSummaryView>

    /** 표시용 최소 정보. 이메일을 프로젝션하지 않으므로 복호화가 없다. */
    fun findPublicProfile(memberId: Long): MemberPublicProfileView?

    /** 여러 회원의 표시용 정보를 **IN 절 한 번**으로. 없는 id는 결과에 키가 없다. */
    fun findPublicProfilesIn(memberIds: List<Long>): Map<Long, MemberPublicProfileView>
}
