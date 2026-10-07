package org.around.ieuri.community.adapter.client

import org.around.ieuri.community.adapter.client.mapper.toPostSpotView
import org.around.ieuri.community.application.shared.dto.PostSpotView
import org.around.ieuri.community.application.shared.required.SpotLookupPort
import org.around.ieuri.community.domain.shared.ContentLanguage
import org.around.ieuri.tourism.application.catalog.provided.SpotCatalogQueryUseCase
import org.springframework.stereotype.Component

/**
 * tourism의 인바운드 포트로 spot 요약을 조회한다.
 * - `spotDetail`이 아니라 `spotSummary`를 쓴다 — detail은 캐시 미스 시 TourAPI를 왕복한다.
 */
@Component
class SpotLookupAdapter(
    private val spotQueryUseCase: SpotCatalogQueryUseCase,
) : SpotLookupPort {

    override fun findSummary(catalogId: Long, language: ContentLanguage): PostSpotView? = spotQueryUseCase.spotSummary(catalogId, language.name)?.toPostSpotView()

    /** 여러 spot을 한 번에 조회한다. 없거나 [language]에서 해소되지 않는 spot은 결과에서 빠진다. */
    override fun findSummaries(catalogIds: List<Long>, language: ContentLanguage): Map<Long, PostSpotView> = spotQueryUseCase.spotSummaries(catalogIds.distinct(), language.name)
        .mapValues { (_, summary) -> summary.toPostSpotView() }
}
