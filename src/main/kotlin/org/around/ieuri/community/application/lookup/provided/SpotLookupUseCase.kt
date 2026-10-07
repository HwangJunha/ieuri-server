package org.around.ieuri.community.application.lookup.provided

import org.around.ieuri.community.domain.lookup.SpotSummary
import org.around.ieuri.community.domain.shared.ContentLanguage

/** 장소(tourism) 조회. 글·질문·대화 서비스와 GraphQL DataLoader가 함께 쓴다. */
interface SpotLookupUseCase {
    /** 카탈로그 id로 찾는다(SPEC-0019). [language]에서 해소되지 않으면 null이며, 그것이 대상 유효성 판정이다(SPEC-0029). */
    fun spot(catalogId: Long, language: ContentLanguage): SpotSummary?

    /** 여러 장소를 한 번에. [language]에서 해소되지 않는 id는 결과에 키가 없다. */
    fun spots(catalogIds: List<Long>, language: ContentLanguage): Map<Long, SpotSummary>
}
