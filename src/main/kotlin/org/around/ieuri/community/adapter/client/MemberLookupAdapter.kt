package org.around.ieuri.community.adapter.client

import org.around.ieuri.auth.application.member.provided.MemberQueryUseCase
import org.around.ieuri.community.adapter.client.mapper.toMemberSummary
import org.around.ieuri.community.application.lookup.required.MemberLookupPort
import org.around.ieuri.community.domain.lookup.MemberSummary
import org.springframework.stereotype.Component

/**
 * auth의 인바운드 포트로 회원 공개 프로필을 조회한다.
 * - `member`가 아니라 `memberPublicProfile`을 쓴다 — `member`는 이메일을 복호화하는 관리자용이다.
 */
@Component
class MemberLookupAdapter(
    private val memberQueryUseCase: MemberQueryUseCase,
) : MemberLookupPort {

    override fun findAuthor(memberId: Long): MemberSummary? = memberQueryUseCase.memberPublicProfile(memberId)?.toMemberSummary()

    /** 한 번의 조회로 여러 작성자를 가져온다. */
    override fun findAuthors(memberIds: List<Long>): Map<Long, MemberSummary> = memberQueryUseCase.memberPublicProfiles(memberIds.distinct())
        .mapValues { (_, profile) -> profile.toMemberSummary() }
}
