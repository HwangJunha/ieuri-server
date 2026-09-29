package org.around.ieuri.common.error

/**
 * 카테고리 다중 필터에 빈 배열이 왔다(SPEC-0020). 관광지·커뮤니티가 같은 규칙으로 거절한다.
 * - "필터 없음"으로도 "결과 없음"으로도 해석하지 않는다 — 클라이언트 결함으로 본다.
 */
class EmptyCategorySetException(message: String = "카테고리를 하나 이상 선택해 주세요.") : RuntimeException(message) {
    val code: String = CODE

    companion object {
        const val CODE = "EMPTY_CATEGORY_SET"
    }
}
