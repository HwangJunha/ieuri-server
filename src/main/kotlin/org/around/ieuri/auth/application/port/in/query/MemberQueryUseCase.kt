package org.around.ieuri.auth.application.port.`in`.query

import org.around.ieuri.auth.application.query.MembersQuery
import org.around.ieuri.auth.application.query.MeView
import org.around.ieuri.auth.application.query.MemberDetailView
import org.around.ieuri.auth.application.query.MembersPage

/**
 * 회원 조회 유스케이스(CQRS 읽기 측). 도메인적으로 모두 Member 조회이므로 하나로 묶는다.
 * 인가는 인바운드 리졸버에서 판정한다: me는 인증만, member/members는 member:read 권한.
 */
interface MemberQueryUseCase {
    /** 본인 정보 조회(인증만 필요). */
    fun me(memberId: Long): MeView

    /** 관리자 회원 단건 상세(member:read). */
    fun member(memberId: Long): MemberDetailView

    /** 관리자 회원 목록(member:read). */
    fun members(query: MembersQuery): MembersPage
}
