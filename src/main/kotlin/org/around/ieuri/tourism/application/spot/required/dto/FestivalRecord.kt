package org.around.ieuri.tourism.application.spot.required.dto

import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.FestivalPeriod

/**
 * 축제 목록에서 읽은 한 행(SPEC-0007). 원본([org.around.ieuri.tourism.domain.spot.Spot])을 찾아 그 루트가 축제를 만든다.
 * - [progressType]·[festivalType]은 원문 문자열 그대로이며, 빈 값은 null로 정규화되어 온다.
 */
data class FestivalRecord(
    val contentId: String,
    val language: DomainLocale,
    val period: FestivalPeriod,
    val progressType: String?,
    val festivalType: String?,
) {
    init {
        // contentId가 없으면 원본을 찾을 수 없다.
        require(contentId.isNotBlank()) { "contentId는 필수입니다." }
    }
}
