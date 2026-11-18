package org.around.ieuri.community.application.lookup.required

import org.around.ieuri.community.domain.lookup.SpotSummary
import org.around.ieuri.community.domain.shared.ContentLanguage

/** 글이 붙은 spot 조회(tourism). */
interface SpotLookupPort {
    /**
     * 피드 카드용 요약. 인자는 카탈로그 id다(SPEC-0019).
     * - [language]에서 해소되지 않으면 null이며, 그것이 대상 유효성 판정이다(SPEC-0029).
     */
    fun findSummary(catalogId: Long, language: ContentLanguage): SpotSummary?

    /** 여러 장소의 요약을 한 번에. [language]에서 해소되지 않는 id는 결과에 키가 없다. */
    fun findSummaries(catalogIds: List<Long>, language: ContentLanguage): Map<Long, SpotSummary>
}
