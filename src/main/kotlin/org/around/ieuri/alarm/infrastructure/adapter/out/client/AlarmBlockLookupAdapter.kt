package org.around.ieuri.alarm.infrastructure.adapter.out.client

import org.around.ieuri.alarm.application.port.out.client.BlockLookupPort
import org.around.ieuri.community.application.post.provided.CommunityQueryUseCase
import org.springframework.stereotype.Component

/** 차단 관계 조회를 community의 인바운드 포트로 넘긴다(SPEC-0028 R9). */
@Component
class AlarmBlockLookupAdapter(
    private val communityQueryUseCase: CommunityQueryUseCase,
) : BlockLookupPort {

    override fun isBlockedBetween(memberId: Long, counterpartId: Long): Boolean = communityQueryUseCase.isBlockedBetween(memberId, counterpartId)
}
