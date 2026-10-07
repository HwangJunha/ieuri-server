package org.around.ieuri.tourism.domain.spot

import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.FestivalPeriod

/**
 * 원본 관광지의 축제 정보(SPEC-0007). 축제 전용 값만 담는다 — 공통 필드는 같은 원본([Spot])에 있다.
 * - [progressType]·[festivalType]은 원문 문자열 그대로이며, 빈 값은 null로 정규화되어 온다.
 */
data class SpotFestival(
    val contentId: String,
    val language: DomainLocale,
    val period: FestivalPeriod,
    val progressType: String?,
    val festivalType: String?,
) {
    init {
        // contentId가 없으면 spot 행에 연결할 수 없다.
        require(contentId.isNotBlank()) { "contentId는 필수입니다." }
    }
}
