package org.around.ieuri.community.domain.vo

/**
 * 글이 붙은 대상의 카테고리. 커뮤니티 상단 추천 칩이 이 축으로 피드를 거른다.
 *
 * tourism의 SpotCategory와 값 집합이 같지만 별도로 둔다 — 도메인 간 내부 타입을 직접 가져다
 * 쓰지 않는 규칙 때문이며, 커뮤니티가 분리될 때 따라가야 하는 타입이기도 하다.
 *
 * **대상 종류에 따라 카테고리가 없을 수 있다.** 관광지는 전부 카테고리를 갖지만 다른 종류가
 * 붙으면 null이며, 그 경우 카테고리 칩으로 걸러지지 않는다.
 */
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
