package org.around.ieuri.tourism.application.port.`in`.command

/**
 * 잘못 접힌 카탈로그를 가르는 유스케이스(SPEC-0029). 배치가 배정 뒤에 호출한다.
 *
 * **왜 필요한가.** 매칭 키가 제목에서 한글만 남기므로 이름이 같은 다른 장소가 같은 키를 갖고
 * (`카페 림` 인천·광주), 비한국어 배정은 후보가 유일하면 좌표를 묻지 않는다. 그 둘이 겹치면
 * 250km 떨어진 장소가 한 카탈로그에 붙는다.
 *
 * **배정을 고치지 않고 사후 교정을 택한 이유는** 배정 시점에는 그 카탈로그에 앞으로 어떤
 * 원본이 더 붙을지 알 수 없기 때문이다. 전량이 붙고 난 뒤라야 "이 카탈로그가 실제로 몇
 * 장소인가"를 물을 수 있다.
 */
interface SpotCatalogSplitUseCase {

    /**
     * 서로 다른 장소가 접힌 카탈로그를 찾아 가른다.
     *
     * **배정이 전부 끝난 뒤에 부른다** — 배정 중에 부르면 아직 안 붙은 원본을 빼고 판정한다.
     * 노출 갱신보다는 **앞이다**: 분할이 옛 카탈로그의 번역 행을 지우고 새 카탈로그에
     * 심으므로, 그 결과가 노출 판정에 반영되어야 구독자가 받는 정본이 맞는다.
     */
    fun splitMismatchedCatalogs(): CatalogSplitResult
}

/**
 * 분할 결과.
 *
 * [catalogsSplit]은 갈라진 옛 카탈로그 수, [catalogsCreated]는 새로 만든 카탈로그 수다.
 * 한 카탈로그가 세 장소였으면 각각 1과 2가 된다.
 */
data class CatalogSplitResult(
    val catalogsSplit: Int,
    val catalogsCreated: Int,
    val spotsMoved: Int,
)
