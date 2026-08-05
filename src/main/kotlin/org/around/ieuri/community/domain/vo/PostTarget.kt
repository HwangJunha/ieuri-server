package org.around.ieuri.community.domain.vo

/**
 * 글이 붙는 대상의 종류.
 *
 * `community`는 특정 도메인의 부속물이 아니라 커뮤니티 플랫폼이다. 지금은 관광지에만 글이
 * 붙지만 앞으로 상품·경매 등 다른 도메인의 대상에도 붙는다 — 값을 늘리고 그 종류의 조회
 * 어댑터를 등록하면 스키마도 도메인도 그대로다.
 */
enum class PostTargetType {
    SPOT,
}

/**
 * 글이 붙는 대상. 종류와 식별자가 함께여야 대상 하나를 가리킨다 —
 * 종류가 다르면 같은 숫자라도 다른 대상이다.
 */
data class PostTarget(
    val type: PostTargetType,
    val id: Long,
) {
    init {
        require(id > 0) { "대상 id는 양수여야 합니다." }
    }
}
