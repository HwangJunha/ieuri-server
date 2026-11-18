package org.around.ieuri.community.application.lookup

import org.around.ieuri.community.application.lookup.provided.SpotLookupUseCase
import org.around.ieuri.community.application.lookup.required.SpotLookupPort
import org.around.ieuri.community.domain.lookup.SpotSummary
import org.around.ieuri.community.domain.shared.ContentLanguage
import org.springframework.stereotype.Service

/** 장소 조회. 다른 context 호출이라 community 트랜잭션을 열지 않는다. */
@Service
class SpotLookupService(
    private val spotLookupPort: SpotLookupPort,
) : SpotLookupUseCase {
    override fun spot(catalogId: Long, language: ContentLanguage): SpotSummary? = spotLookupPort.findSummary(catalogId, language)

    override fun spots(catalogIds: List<Long>, language: ContentLanguage): Map<Long, SpotSummary> = spotLookupPort.findSummaries(catalogIds, language)
}
