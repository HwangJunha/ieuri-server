package org.around.ieuri.common.error

/**
 * 카테고리 다중 필터에 빈 배열이 왔다(SPEC-0020). **관광지·커뮤니티가 같은 규칙을 쓴다** —
 * 같은 개념을 두 도메인이 다르게 거절하면 클라이언트가 화면마다 다른 규칙을 외워야 한다.
 *
 * "필터 없음"으로도 "결과 없음"으로도 해석하지 않는다 — UI는 선택 0개를 만들 수 없으므로
 * 빈 배열이 도착했다면 클라이언트 결함이고, 어느 쪽으로 읽어도 그 결함을 가린다.
 *
 * `common`에 두는 이유는 두 도메인이 함께 던지기 때문이다. 한쪽 도메인에 두면 다른 쪽이
 * 그것을 import해야 해서 도메인 경계가 무너진다.
 */
class EmptyCategorySetException(message: String = "카테고리를 하나 이상 선택해 주세요.") : RuntimeException(message) {
    val code: String = CODE

    companion object {
        const val CODE = "EMPTY_CATEGORY_SET"
    }
}
