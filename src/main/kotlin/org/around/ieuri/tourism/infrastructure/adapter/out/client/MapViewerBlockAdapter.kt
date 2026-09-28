package org.around.ieuri.tourism.infrastructure.adapter.out.client

import org.around.ieuri.community.application.port.`in`.query.CommunityQueryUseCase
import org.around.ieuri.tourism.application.port.out.client.MapViewerBlockPort
import org.springframework.stereotype.Component

/**
 * 공유 지도 조회자의 차단 판정(SPEC-0030 R16). `community`의 **인바운드 포트**로만 묻는다 —
 * 차단은 community의 소유이며 tourism이 그 테이블을 직접 읽지 않는다.
 *
 * tourism의 application·domain은 [MapViewerBlockPort]만 알고 community라는 이름조차 모른다.
 *
 * 목록이 아니라 판정을 쓰는 이유는 상대 한 명만 두고 볼지 말지를 정하기 때문이다.
 */
@Component
class MapViewerBlockAdapter(
    private val communityQueryUseCase: CommunityQueryUseCase,
) : MapViewerBlockPort {

    override fun isBlockedBetween(memberId: Long, counterpartId: Long): Boolean = communityQueryUseCase.isBlockedBetween(memberId, counterpartId)
}
