package org.around.ieuri.tourism.domain.vo

/**
 * 관광지 운영 정보(값 객체). TourAPI detailIntro2는 카테고리마다 응답 필드가 완전히 다르므로
 * (관광지 `usetime`, 음식점 `opentimefood`, 문화시설 `usetimeculture` …), **어댑터가 의미가 같은 필드를
 * 공통 이름으로 정규화**한 결과만 도메인에 들어온다. 도메인은 TourAPI 필드명을 모른다.
 *
 * 카테고리에 해당 개념이 없으면 null이다(예: 여행코스엔 주차·휴무 개념이 없어 전부 null).
 * 전부 null이면 표시할 운영 정보가 없다는 뜻이므로 [isEmpty]로 판정해 SpotDetail.intro 자체를 null로 둔다.
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
    /** 모든 필드가 비었으면 true — 빈 껍데기를 응답에 내리지 않기 위한 판정. */
    fun isEmpty(): Boolean = listOf(
        businessHours, closedDays, parking, inquiryPhone, fee, petAllowed, creditCardAccepted,
    ).all { it == null }
}

/**
 * 이용안내 1건(detailInfo2의 반복 항목). 항목명/내용 쌍으로 정규화한다.
 * 둘 다 값이 있어야 의미가 있으므로 non-null이며, 빈 항목은 어댑터가 걸러 목록에 넣지 않는다.
 */
data class SpotInfoItem(
    val name: String,
    val text: String,
) {
    init {
        require(name.isNotBlank()) { "이용안내 항목명은 비어 있을 수 없습니다." }
        require(text.isNotBlank()) { "이용안내 항목 내용은 비어 있을 수 없습니다." }
    }
}
