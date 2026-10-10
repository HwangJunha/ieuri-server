package org.around.ieuri.auth.application.member.provided
import org.around.ieuri.auth.application.member.provided.dto.MeView
import org.around.ieuri.auth.application.member.provided.dto.MemberDetailView
import org.around.ieuri.auth.application.member.provided.dto.MemberPublicProfileView
import org.around.ieuri.auth.application.member.provided.dto.MemberSummaryView
import org.around.ieuri.auth.application.member.provided.dto.MembersQuery
import org.around.ieuri.common.pagination.CursorPage

/** 회원 조회 유스케이스. 인가는 리졸버가 판정한다: me는 인증만, member/members는 member:read. */
interface MemberQueryUseCase {
    /** 본인 정보 조회(인증만 필요). */
    fun me(memberId: Long): MeView

    /** 미인증 게이트가 요청마다 부른다. 없는 회원은 거짓이다. */
    fun isEmailVerified(memberId: Long): Boolean

    /** 관리자 회원 단건 상세(member:read). */
    fun member(memberId: Long): MemberDetailView

    /** 관리자 회원 목록(member:read, 커서 페이지네이션). */
    fun members(query: MembersQuery): CursorPage<MemberSummaryView>

    /**
     * 표시용 최소 회원 정보(닉네임·프로필 이미지). 다른 도메인이 작성자를 표시할 때 쓴다.
     * - 권한이 필요 없고 이메일을 복호화하지 않는다. 부재는 예외가 아니라 null이다.
     */
    fun memberPublicProfile(memberId: Long): MemberPublicProfileView?

    /** 여러 회원의 표시용 정보를 **한 번에**. 목록을 그리는 호출자용이다. */
    fun memberPublicProfiles(memberIds: List<Long>): Map<Long, MemberPublicProfileView>
}
