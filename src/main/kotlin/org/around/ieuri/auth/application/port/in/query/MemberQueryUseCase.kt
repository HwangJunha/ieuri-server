package org.around.ieuri.auth.application.port.`in`.query

import org.around.ieuri.auth.application.query.MembersQuery
import org.around.ieuri.auth.application.query.MeView
import org.around.ieuri.auth.application.query.MemberDetailView
import org.around.ieuri.auth.application.query.MemberPublicProfileView
import org.around.ieuri.auth.application.query.MemberSummaryView
import org.around.ieuri.common.pagination.CursorPage

/**
 * 회원 조회 유스케이스(CQRS 읽기 측). 도메인적으로 모두 Member 조회이므로 하나로 묶는다.
 * 인가는 인바운드 리졸버에서 판정한다: me는 인증만, member/members는 member:read 권한.
 */
interface MemberQueryUseCase {
    /** 본인 정보 조회(인증만 필요). */
    fun me(memberId: Long): MeView

    /** 관리자 회원 단건 상세(member:read). */
    fun member(memberId: Long): MemberDetailView

    /** 관리자 회원 목록(member:read, 커서 페이지네이션). */
    fun members(query: MembersQuery): CursorPage<MemberSummaryView>

    /**
     * 표시용 최소 회원 정보(닉네임·프로필 이미지). 권한이 필요 없고 **이메일을 복호화하지 않는다**.
     *
     * 다른 도메인이 작성자를 표시할 때 쓰는 진입점이다. 그 용도로 [member]를 쓰면 목록 한 페이지에
     * 복호화가 20번 붙고, 커뮤니티 표면에 필요 없는 이메일·권한·상태까지 딸려 온다.
     * 부재는 예외가 아니라 null이다.
     */
    fun memberPublicProfile(memberId: Long): MemberPublicProfileView?

    /** 여러 회원의 표시용 정보를 **한 번에**. 목록을 그리는 호출자용이다. */
    fun memberPublicProfiles(memberIds: List<Long>): Map<Long, MemberPublicProfileView>
}
