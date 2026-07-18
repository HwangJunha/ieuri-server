package org.around.ieuri.auth.application.query

import org.around.ieuri.auth.application.exception.MemberNotFoundException
import org.around.ieuri.auth.application.port.`in`.query.MemberQueryUseCase
import org.around.ieuri.auth.application.port.out.query.MemberQueryPort
import org.springframework.stereotype.Service

/**
 * 회원 조회(본인/단건 상세/목록). 애그리거트를 우회해 읽기 포트가 프로젝션한 read DTO를 그대로 반환한다.
 * 인가는 인바운드 리졸버에서 판정하고, 여기선 조회에 집중한다. 목록은 이메일 미포함(최소수집).
 */
@Service
class MemberQueryHandler(
    private val memberQueryPort: MemberQueryPort,
) : MemberQueryUseCase {

    override fun me(memberId: Long): MeView =
        memberQueryPort.findMe(memberId)
            ?: throw MemberNotFoundException("회원을 찾을 수 없습니다.")

    override fun member(memberId: Long): MemberDetailView =
        memberQueryPort.findMemberDetail(memberId)
            ?: throw MemberNotFoundException("회원을 찾을 수 없습니다.")

    override fun members(query: MembersQuery): MembersPage = memberQueryPort.findMembers(query)
}
