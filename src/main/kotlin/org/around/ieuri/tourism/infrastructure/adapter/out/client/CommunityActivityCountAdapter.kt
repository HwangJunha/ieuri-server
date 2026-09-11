package org.around.ieuri.tourism.infrastructure.adapter.out.client

import org.around.ieuri.community.application.port.`in`.query.CommunityQueryUseCase
import org.around.ieuri.community.application.port.`in`.query.SpotQaQueryUseCase
import org.around.ieuri.tourism.application.port.out.client.ActivityCounts
import org.around.ieuri.tourism.application.port.out.client.CommunityActivityCountPort
import org.springframework.stereotype.Component

/**
 * 장소별 커뮤니티 활동 수(SPEC-0035 R9). community의 **인바운드 포트**를 통해서만 읽는다 —
 * tourism의 application·domain은 [CommunityActivityCountPort]만 알고 community라는 이름을 모른다.
 * 숫자만 꺼내 옮기므로 community의 read DTO가 이 경계를 넘지 않는다.
 */
@Component
class CommunityActivityCountAdapter(
    private val communityQueryUseCase: CommunityQueryUseCase,
    private val spotQaQueryUseCase: SpotQaQueryUseCase,
) : CommunityActivityCountPort {

    override fun countByCatalogIds(catalogIds: Collection<Long>): Map<Long, ActivityCounts> {
        if (catalogIds.isEmpty()) return emptyMap()
        val ids = catalogIds.distinct()
        val posts = communityQueryUseCase.postCountsByCatalogIds(ids)
        val qa = spotQaQueryUseCase.qaCountsByCatalogIds(ids)
        return ids.associateWith { id ->
            ActivityCounts(
                posts = posts[id] ?: 0L,
                questions = qa[id]?.questions ?: 0L,
                answers = qa[id]?.answers ?: 0L,
            )
        }
    }
}
