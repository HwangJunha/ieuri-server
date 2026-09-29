package org.around.ieuri.common.context

/**
 * 요청을 보낸 앱의 식별자 — 상단 칩 구성을 고르는 열쇠다. 회원·인증과 무관하다.
 * 그 앱의 구성이 없으면 칩은 빈 목록이 된다.
 */
@JvmInline
value class AppKey(val value: String) {
    init {
        require(value.isNotBlank()) { "appKey는 비어 있을 수 없습니다." }
        require(value.length <= MAX_LENGTH) { "appKey는 ${MAX_LENGTH}자를 넘을 수 없습니다." }
    }

    companion object {
        const val MAX_LENGTH = 50

        /** 헤더가 없거나 형식이 어긋날 때 쓰는 앱. 현재 유일하게 출시된 앱이다. */
        val DEFAULT = AppKey("k-trip-spot")

        /** 헤더 문자열을 식별자로. 비어 있거나 너무 길면 폴백한다 — 조회 하나 때문에 요청을 깨지 않는다. */
        fun fromHeaderOrDefault(raw: String?): AppKey {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty() || trimmed.length > MAX_LENGTH) return DEFAULT
            return AppKey(trimmed)
        }
    }
}
