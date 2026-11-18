package org.around.ieuri.community.application.shared

/** 커넥션 `first` 상한(POL-018). 다섯 애그리게이트의 목록 조회가 같은 상한을 쓴다. */
data class PagePolicy(val maxPageSize: Int) {
    /** 상한 초과는 오류가 아니라 절삭이다. 1 미만은 1이다. */
    fun clamp(first: Int): Int = first.coerceIn(1, maxPageSize)
}
