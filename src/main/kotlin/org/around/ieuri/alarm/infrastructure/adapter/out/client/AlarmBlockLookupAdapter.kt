package org.around.ieuri.alarm.infrastructure.adapter.out.client

import org.around.ieuri.alarm.application.port.out.client.BlockLookupPort
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

    override fun isBlockedBetween(memberId: Long, counterpartId: Long): Boolean = communityQueryUseCase.isBlockedBetween(memberId, counterpartId)
}
