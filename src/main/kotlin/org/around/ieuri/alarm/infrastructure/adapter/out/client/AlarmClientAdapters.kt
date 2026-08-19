package org.around.ieuri.alarm.infrastructure.adapter.out.client

import org.around.ieuri.alarm.application.port.out.client.BlockLookupPort
import org.around.ieuri.alarm.application.port.out.client.MemberNicknameLookupPort
import org.around.ieuri.auth.application.port.`in`.query.MemberQueryUseCase
import org.around.ieuri.community.application.port.`in`.query.CommunityQueryUseCase
import org.springframework.stereotype.Component

/**
 * 차단 관계 조회를 community의 인바운드 포트로 넘긴다(SPEC-0028 R9).
 *
 * **이 어댑터가 있는 이유는 방향이다.** alarm의 application은 community를 알지 못하고
 * 자기 포트만 안다 — 도메인 간 참조를 인프라 한 겹으로 가둔다.
 */
@Component
class AlarmBlockLookupAdapter(
    private val communityQueryUseCase: CommunityQueryUseCase,
) : BlockLookupPort {

    override fun isBlockedBetween(memberId: Long, counterpartId: Long): Boolean =
        communityQueryUseCase.isBlockedBetween(memberId, counterpartId)
}

/**
 * 알림 제목에 넣을 닉네임을 auth의 인바운드 포트로 읽는다.
 *
 * 표시용 최소 정보를 주는 `memberPublicProfile`을 쓴다 — 이메일을 복호화하지 않는 진입점이다.
 */
@Component
class AlarmMemberNicknameLookupAdapter(
    private val memberQueryUseCase: MemberQueryUseCase,
) : MemberNicknameLookupPort {

    override fun findNickname(memberId: Long): String? =
        memberQueryUseCase.memberPublicProfile(memberId)?.nickname
}
