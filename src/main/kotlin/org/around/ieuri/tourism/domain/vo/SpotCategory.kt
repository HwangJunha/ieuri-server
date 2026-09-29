package org.around.ieuri.tourism.domain.vo

/**
 * 관광지 카테고리 코드(SPEC-0020). enum이 아니라 `category` 테이블을 따르는 열린 값이다.
 * - 존재 여부는 검증하지 않는다. 없는 코드로 거른 조회는 결과가 없을 뿐이다.
 */
@JvmInline
value class SpotCategory(val code: String) {
    init {
        require(code.isNotBlank()) { "카테고리 코드는 비어 있을 수 없습니다." }
        require(code.length <= MAX_LENGTH) { "카테고리 코드는 ${MAX_LENGTH}자를 넘을 수 없습니다." }
    }

    override fun toString(): String = code

    companion object {
        const val MAX_LENGTH = 30

        /** 코드가 이름으로 불러야 하는 값만 둔 상수. 새 카테고리는 여기 추가하지 않는다. */
        val TOURIST_SPOT = SpotCategory("TOURIST_SPOT")
        val FESTIVAL_EVENT = SpotCategory("FESTIVAL_EVENT")

        /** DB·업스트림에서 읽은 문자열을 트림해 코드로. 비었거나 길이 초과면 null이다. */
        fun ofOrNull(code: String?): SpotCategory? = code?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_LENGTH }?.let { SpotCategory(it) }
    }
}
