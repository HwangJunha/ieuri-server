package org.around.ieuri.auth.application.port.out.query

import org.around.ieuri.auth.application.query.MembersQuery
import org.around.ieuri.auth.application.query.MeView
import org.around.ieuri.auth.application.query.MemberDetailView
import org.around.ieuri.auth.application.query.MemberPublicProfileView
import org.around.ieuri.auth.application.query.MemberSummaryView
import org.around.ieuri.common.pagination.CursorPage

/**
 * 회원 읽기 포트(CQRS 읽기 측). 애그리거트를 거치지 않고 read DTO를 직접 반환한다.
 * 평문 이메일이 필요한 조회(me/단건)는 구현(query 어댑터)이 EmailEncryptorPort로 복호화해 채운다.
 * 도메인 포트 대칭을 강요하지 않는다(읽기 전용).
 */
interface MemberQueryPort {
    fun findMe(memberId: Long): MeView?

    fun findMemberDetail(memberId: Long): MemberDetailView?

    fun findMembers(query: MembersQuery): CursorPage<MemberSummaryView>

    /** 표시용 최소 정보. 이메일을 프로젝션하지 않으므로 복호화가 없다. */
    fun findPublicProfile(memberId: Long): MemberPublicProfileView?
}
