package org.around.ieuri.tourism.domain.vo

/**
 * 관광지 카테고리 코드(SPEC-0020). **enum이 아니라 열린 값이다** — 새 카테고리가 `category`
 * 테이블에 추가되면 서버 배포 없이 그대로 흘러간다.
 *
 * enum이던 시절에는 카테고리 하나를 늘리려면 Kotlin enum·GraphQL enum·CHECK 제약 3개를 함께
 * 고치고 배포해야 했고, 표시 문구와 아이콘은 앱에 박혀 있어 앱 업데이트까지 필요했다. 이 타입은
 * 코드만 들고 있으며 표시 정보(label·iconUrl)는 `category` 테이블이 갖는다.
 *
 * **값 자체는 검증하지 않는다.** 존재하지 않는 코드로 걸러진 조회는 결과가 없을 뿐이고, 그것이
 * 알 수 없는 코드를 거절하는 것보다 낫다 — 카테고리는 앞으로 늘어나는 축이라, 서버가 아는
 * 목록으로 요청을 막으면 이 변경의 목적이 사라진다.
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

        /**
         * 기존 코드가 이름으로 참조하던 값들. **특별한 지위는 없다** — 시드에 들어 있는 9개 중
         * 코드가 실제로 이름을 불러야 하는 것만 남긴 상수다. 새 카테고리는 여기 추가하지 않는다.
         */
        val TOURIST_SPOT = SpotCategory("TOURIST_SPOT")
        val FESTIVAL_EVENT = SpotCategory("FESTIVAL_EVENT")

        /** DB·업스트림에서 읽은 문자열을 코드로. 비어 있으면 null이다. */
        fun ofOrNull(code: String?): SpotCategory? =
            code?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_LENGTH }?.let { SpotCategory(it) }
    }
}
