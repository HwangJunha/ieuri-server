package org.around.ieuri.tourism.domain.vo

/**
 * 언어 중립 관광지 카테고리. TourAPI contentTypeId 코드 체계(12/76/38/79…)를 도메인이 직접 알지 않도록
 * 안정 식별자로만 표현한다(POL-023: enum은 표시 문자열이 아니다). 언어별 코드 매핑과 가용성 판정은
 * 어댑터/설정의 책임이며, 카테고리 자체의 언어 비대칭(아래 [koreanOnly]/[foreignOnly])만 도메인이 안다.
 */
enum class SpotCategory {
    TOURIST_SPOT,
    CULTURAL_FACILITY,
    FESTIVAL_EVENT,

    /** 한국어(KO) 서비스에서만 제공됨. */
    TRAVEL_COURSE,
    LEISURE_SPORTS,
    LODGING,
    SHOPPING,
    RESTAURANT,

    /** 외국어(EN/JA/ZH) 서비스에서만 제공됨. */
    TRANSPORT,
    ;

    /** 국문 전용 카테고리(외국어 서비스엔 없음). */
    val koreanOnly: Boolean get() = this == TRAVEL_COURSE

    /** 외국어 전용 카테고리(국문 서비스엔 없음). */
    val foreignOnly: Boolean get() = this == TRANSPORT

    /** 주어진 언어에서 이 카테고리가 유효한가. 비대칭(여행코스/교통)을 흡수한다. */
    fun isAvailableIn(locale: DomainLocale): Boolean = when {
        koreanOnly -> locale.isKorean
        foreignOnly -> !locale.isKorean
        else -> true
    }

    companion object {
        /** 주어진 언어에서 노출 가능한 카테고리 목록(availableSpotCategories). */
        fun availableIn(locale: DomainLocale): List<SpotCategory> =
            entries.filter { it.isAvailableIn(locale) }
    }
}
