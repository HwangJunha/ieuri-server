package org.around.ieuri.alarm.adapter.client

import org.around.ieuri.alarm.application.push.required.BlockLookupPort
import org.around.ieuri.community.application.block.provided.BlockQueryUseCase
import org.springframework.stereotype.Component

/** 차단 관계 조회를 community의 인바운드 포트로 넘긴다(SPEC-0028 R9). */
@Component
class AlarmBlockLookupAdapter(
    private val blockQueryUseCase: BlockQueryUseCase,
) : BlockLookupPort {

    override fun isBlockedBetween(memberId: Long, counterpartId: Long): Boolean = blockQueryUseCase.isBlockedBetween(memberId, counterpartId)
}
