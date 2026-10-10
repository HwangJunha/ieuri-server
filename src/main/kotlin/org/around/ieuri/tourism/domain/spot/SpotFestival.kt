package org.around.ieuri.tourism.domain.spot

import org.around.ieuri.tourism.domain.shared.FestivalPeriod

/**
 * 원본 관광지의 축제 정보(SPEC-0007). [Spot.hold]로만 만든다 — 축제 전용 값만 담고, 공통 필드는 같은 원본에 있다.
 * - [progressType]·[festivalType]은 원문 문자열 그대로이며, 빈 값은 null로 정규화되어 온다.
 */
data class SpotFestival(
    val spotId: Long,
    val period: FestivalPeriod,
    val progressType: String?,
    val festivalType: String?,
)
