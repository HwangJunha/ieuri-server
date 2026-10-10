package org.around.ieuri.auth.application.member
import org.around.ieuri.auth.application.member.provided.MemberQueryUseCase
import org.around.ieuri.auth.application.member.provided.dto.MeView
import org.around.ieuri.auth.application.member.provided.dto.MemberDetailView
import org.around.ieuri.auth.application.member.provided.dto.MemberPublicProfileView
import org.around.ieuri.auth.application.member.provided.dto.MemberSummaryView
import org.around.ieuri.auth.application.member.provided.dto.MembersQuery
import org.around.ieuri.auth.application.member.provided.exception.MemberNotFoundException
import org.around.ieuri.auth.application.member.required.MemberQueryPort
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.persistence.ExposedConfig
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 회원 조회. 읽기 포트의 read DTO를 그대로 반환하며 인가는 리졸버가 판정한다. */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX, readOnly = true)
class MemberQueryService(
    private val memberQueryPort: MemberQueryPort,
) : MemberQueryUseCase {

    override fun me(memberId: Long): MeView = memberQueryPort.findMe(memberId)
        ?: throw MemberNotFoundException("회원을 찾을 수 없습니다.")

    override fun isEmailVerified(memberId: Long): Boolean = memberQueryPort.isEmailVerified(memberId)

    override fun member(memberId: Long): MemberDetailView = memberQueryPort.findMemberDetail(memberId)
        ?: throw MemberNotFoundException("회원을 찾을 수 없습니다.")

    override fun members(query: MembersQuery): CursorPage<MemberSummaryView> = memberQueryPort.findMembers(query)

    /** 부재는 예외가 아니라 null이다 — 호출자가 존재 확인 용도로도 쓴다. */
    override fun memberPublicProfile(memberId: Long): MemberPublicProfileView? = memberQueryPort.findPublicProfile(memberId)

    override fun memberPublicProfiles(memberIds: List<Long>): Map<Long, MemberPublicProfileView> = memberQueryPort.findPublicProfilesIn(memberIds)
}
