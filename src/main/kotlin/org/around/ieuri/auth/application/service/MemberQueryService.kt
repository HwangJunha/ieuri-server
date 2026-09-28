package org.around.ieuri.auth.application.service

import org.around.ieuri.auth.application.exception.MemberNotFoundException
import org.around.ieuri.auth.application.port.`in`.query.MemberQueryUseCase
import org.around.ieuri.auth.application.port.out.query.MemberQueryPort
import org.around.ieuri.auth.application.query.MeView
import org.around.ieuri.auth.application.query.MemberDetailView
import org.around.ieuri.auth.application.query.MemberPublicProfileView
import org.around.ieuri.auth.application.query.MemberSummaryView
import org.around.ieuri.auth.application.query.MembersQuery
import org.around.ieuri.common.pagination.CursorPage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 회원 조회. 읽기 포트의 read DTO를 그대로 반환하며 인가는 리졸버가 판정한다. */
@Service
@Transactional(readOnly = true)
class MemberQueryService(
    private val memberQueryPort: MemberQueryPort,
) : MemberQueryUseCase {

    override fun me(memberId: Long): MeView = memberQueryPort.findMe(memberId)
        ?: throw MemberNotFoundException("회원을 찾을 수 없습니다.")

    override fun member(memberId: Long): MemberDetailView = memberQueryPort.findMemberDetail(memberId)
        ?: throw MemberNotFoundException("회원을 찾을 수 없습니다.")

    override fun members(query: MembersQuery): CursorPage<MemberSummaryView> = memberQueryPort.findMembers(query)

    /** 부재는 예외가 아니라 null이다 — 호출자가 존재 확인 용도로도 쓴다. */
    override fun memberPublicProfile(memberId: Long): MemberPublicProfileView? = memberQueryPort.findPublicProfile(memberId)

    override fun memberPublicProfiles(memberIds: List<Long>): Map<Long, MemberPublicProfileView> = memberQueryPort.findPublicProfilesIn(memberIds)
}
