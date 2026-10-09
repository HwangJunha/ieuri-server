package org.around.ieuri.tourism.domain.spot

/** 원본에 딸린 상세 본문([Spot.describe]·[Spot.describeAbsence]로만 만든다). [absent]면 [detail]은 원본 값으로 채운 자리표시다. */
data class SpotBody(
    val spotId: Long,
    val detail: SpotDetail,
    val absent: Boolean,
)
