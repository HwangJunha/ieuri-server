package org.around.ieuri.tourism.application.shared.provided

import org.around.ieuri.common.context.AppKey
import org.around.ieuri.tourism.application.shared.provided.dto.AppCategoryCode
import org.around.ieuri.tourism.application.shared.provided.dto.SpotSummaryView

/**
 * 다른 도메인이 tourism을 읽는 진입점. upstream을 호출하지 않으며, 언어는 문자열 코드로 받는다.
 * - 모르는 언어 코드는 폴백 언어로 읽는다.
 */
interface SpotQueryUseCase {
    /** 이 앱·이 언어에 노출되는 카테고리를 구성된 순서대로. 구성이 없으면 빈 목록이다. */
    fun availableCategoryCodes(appKey: AppKey, languageCode: String): List<AppCategoryCode>

    /** 카탈로그가 보유한 최소 식별 정보. [languageCode]에서 해소되지 않으면 예외가 아니라 null이다(SPEC-0029 R-24). */
    fun spotSummary(catalogId: Long, languageCode: String): SpotSummaryView?

    /** 여러 카탈로그 요약을 한 번에. [languageCode]에서 해소되지 않는 id는 결과에 키가 없다. */
    fun spotSummaries(catalogIds: List<Long>, languageCode: String): Map<Long, SpotSummaryView>
}
