package org.around.ieuri.community.adapter.client

import org.around.ieuri.auth.application.port.`in`.query.MemberQueryUseCase
import org.around.ieuri.community.adapter.client.mapper.toMemberSummaryView
import org.around.ieuri.community.application.shared.required.MemberLookupPort
import org.around.ieuri.community.application.shared.required.dto.MemberSummaryView
import org.springframework.stereotype.Component

/**
 * auth의 인바운드 포트로 회원 공개 프로필을 조회한다.
 * - `member`가 아니라 `memberPublicProfile`을 쓴다 — `member`는 이메일을 복호화하는 관리자용이다.
 */
@Component
class MemberLookupAdapter(
    private val memberQueryUseCase: MemberQueryUseCase,
) : MemberLookupPort {

    override fun findAuthor(memberId: Long): MemberSummaryView? = memberQueryUseCase.memberPublicProfile(memberId)?.toMemberSummaryView()

    /** 한 번의 조회로 여러 작성자를 가져온다. */
    override fun findAuthors(memberIds: List<Long>): Map<Long, MemberSummaryView> = memberQueryUseCase.memberPublicProfiles(memberIds.distinct())
        .mapValues { (_, profile) -> profile.toMemberSummaryView() }
}
