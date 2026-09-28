package org.around.ieuri.tourism.domain.vo

/**
 * 관광지 운영 정보. 카테고리별 TourAPI 필드를 어댑터가 공통 이름으로 정규화한 결과다.
 * - 카테고리에 해당 개념이 없으면 null이다. 전부 null이면 [isEmpty]다.
 */
data class SpotIntro(
    val businessHours: String?,
    val closedDays: String?,
    val parking: String?,
    val inquiryPhone: String?,
    val fee: String?,
    val petAllowed: String?,
    val creditCardAccepted: String?,
) {
    /** 모든 필드가 null이면 true. */
    fun isEmpty(): Boolean = listOf(
        businessHours,
        closedDays,
        parking,
        inquiryPhone,
        fee,
        petAllowed,
        creditCardAccepted,
    ).all { it == null }
}

/** 이용안내 1건(항목명/내용 쌍). 둘 다 비어 있을 수 없다. */
data class SpotInfoItem(
    val name: String,
    val text: String,
) {
    init {
        require(name.isNotBlank()) { "이용안내 항목명은 비어 있을 수 없습니다." }
        require(text.isNotBlank()) { "이용안내 항목 내용은 비어 있을 수 없습니다." }
    }
}
