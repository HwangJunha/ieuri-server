package org.around.ieuri.auth.application.member.required

import org.around.ieuri.auth.application.member.provided.dto.MeView
import org.around.ieuri.auth.application.member.provided.dto.MemberAuthoritiesView
import org.around.ieuri.auth.application.member.provided.dto.MemberDetailView
import org.around.ieuri.auth.application.member.provided.dto.MemberPublicProfileView
import org.around.ieuri.auth.application.member.provided.dto.MemberSummaryView
import org.around.ieuri.auth.application.member.provided.dto.MembersQuery
import org.around.ieuri.auth.application.member.provided.dto.RoleInfoView
import org.around.ieuri.auth.domain.member.Agreement
import org.around.ieuri.auth.domain.member.Terms
import org.around.ieuri.common.pagination.CursorPage
import java.time.Instant

/** 회원 읽기 포트. read DTO를 직접 반환하며, 평문 이메일이 필요한 조회(me/단건)는 구현이 복호화한다. */
interface MemberQueryPort {
    fun findMe(memberId: Long): MeView?

    /** 없는 회원은 거짓이다. */
    fun isEmailVerified(memberId: Long): Boolean

    fun findMemberDetail(memberId: Long): MemberDetailView?

    fun findMembers(query: MembersQuery): CursorPage<MemberSummaryView>

    /** 표시용 최소 정보. 이메일을 읽지 않으므로 복호화가 없다. */
    fun findPublicProfile(memberId: Long): MemberPublicProfileView?

    /** 여러 회원의 표시용 정보를 **IN 절 한 번**으로. 없는 id는 결과에 키가 없다. */
    fun findPublicProfilesIn(memberIds: List<Long>): Map<Long, MemberPublicProfileView>

    /** 회원의 역할 코드와 유효 권한 코드(역할들의 권한 합집합). */
    fun findAuthorities(memberId: Long): MemberAuthoritiesView

    /** 시행일이 [now] 이전인 약관 전부(모든 버전). 어느 버전이 시행 중인지는 도메인이 고른다. */
    fun findEffectiveTerms(now: Instant): List<Terms>

    /** 회원이 남긴 동의 전부(지난 버전 포함). */
    fun findAgreements(memberId: Long): List<Agreement>

    /** 부여할 수 있는 역할 전부. 코드 순이다. */
    fun findAssignableRoles(): List<RoleInfoView>
}
