package org.around.ieuri.community.domain.vo

/** 글이 붙은 대상의 카테고리. 커뮤니티 상단 추천 칩이 이 축으로 피드를 거른다. */
enum class TargetCategory {
    TOURIST_SPOT,
    CULTURAL_FACILITY,
    FESTIVAL_EVENT,
    TRAVEL_COURSE,
    LEISURE_SPORTS,
    LODGING,
    SHOPPING,
    RESTAURANT,
    TRANSPORT,
    ;

    companion object {
        /** 상대 도메인의 카테고리 문자열을 옮긴다. 모르는 값이면 null(칩으로 걸러지지 않음). */
        fun fromOrNull(name: String?): TargetCategory? = name?.let { v -> entries.firstOrNull { it.name == v } }
    }
}
