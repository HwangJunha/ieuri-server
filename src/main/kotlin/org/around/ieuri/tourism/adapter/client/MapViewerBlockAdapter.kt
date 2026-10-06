package org.around.ieuri.tourism.adapter.client

import org.around.ieuri.community.application.block.provided.BlockQueryUseCase
import org.around.ieuri.tourism.application.shared.required.MapViewerBlockPort
import org.springframework.stereotype.Component

/** 공유 지도 조회자의 차단 판정(SPEC-0030 R16). 차단을 소유한 `community`의 인바운드 포트로만 묻는다. */
@Component
class MapViewerBlockAdapter(
    private val blockQueryUseCase: BlockQueryUseCase,
) : MapViewerBlockPort {

    override fun isBlockedBetween(memberId: Long, counterpartId: Long): Boolean = blockQueryUseCase.isBlockedBetween(memberId, counterpartId)
}
