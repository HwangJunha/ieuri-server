package org.around.ieuri.auth.application.member

import org.around.ieuri.auth.application.member.provided.MemberQueryUseCase
import org.around.ieuri.auth.application.member.provided.dto.AgreementStatusView
import org.around.ieuri.auth.application.member.provided.dto.MeView
import org.around.ieuri.auth.application.member.provided.dto.MemberAuthoritiesView
import org.around.ieuri.auth.application.member.provided.dto.MemberDetailView
import org.around.ieuri.auth.application.member.provided.dto.MemberPublicProfileView
import org.around.ieuri.auth.application.member.provided.dto.MemberSummaryView
import org.around.ieuri.auth.application.member.provided.dto.MembersQuery
import org.around.ieuri.auth.application.member.provided.dto.RoleInfoView
import org.around.ieuri.auth.application.member.provided.exception.MemberNotFoundException
import org.around.ieuri.auth.application.member.required.MemberQueryPort
import org.around.ieuri.auth.application.member.required.MemberRepository
import org.around.ieuri.auth.domain.member.ActiveTerms
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.persistence.ExposedConfig
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/** 회원 조회. 읽기 포트의 read DTO를 그대로 반환하며 인가는 리졸버가 판정한다. */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX, readOnly = true)
class MemberQueryService(
    private val memberQueryPort: MemberQueryPort,
    private val memberRepository: MemberRepository,
    private val clock: Clock,
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

    /** 시행 중인 약관의 판정(코드별 최신 버전)은 도메인 규칙이라 저장소로 읽어 도메인에 맡긴다. */
    override fun agreements(memberId: Long): List<AgreementStatusView> = ActiveTerms.of(memberRepository.findEffectiveTerms(clock.instant()))
        .statusOf(memberRepository.findAgreements(memberId))
        .map(AgreementStatusView::of)

    override fun assignableRoles(): List<RoleInfoView> = memberQueryPort.findAssignableRoles()

    override fun authoritiesOf(memberId: Long): MemberAuthoritiesView = memberQueryPort.findAuthorities(memberId)
}
