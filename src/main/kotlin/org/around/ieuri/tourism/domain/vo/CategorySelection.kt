package org.around.ieuri.tourism.domain.vo

/**
 * 조회에 적용할 카테고리 선택(SPEC-0020). 비어 있으면 "필터 없음"이며 전 카테고리가 대상이다.
 *
 * 조회 5개 연산이 모두 이 값을 받는다. 빈 배열 거절 규칙을 연산마다 두면 다섯 번 어긋날
 * 기회가 생기므로 [of]에 한 번만 둔다.
 *
 * 선택된 값들은 **OR**로 결합된다.
 *
 * **알 수 없는 코드를 거절하지 않는다.** 그런 코드로 거른 결과는 비어 있을 뿐이며, 그것이
 * 서버가 아는 목록으로 요청을 막는 것보다 낫다 — 카테고리는 데이터로 늘어나는 축이라
 * (`category` 테이블), 코드가 유효 목록을 쥐면 새 카테고리마다 배포가 필요해진다.
 */
@JvmInline
value class CategorySelection private constructor(val values: Set<SpotCategory>) {

    /** 필터를 적용하지 않는가. 비어 있으면 조건 자체를 걸지 않는다. */
    val isUnfiltered: Boolean get() = values.isEmpty()

    /** 조건 생성기에 넘길 코드 목록. 재현 가능하도록 사전순으로 고정한다. */
    fun codes(): List<String> = values.map { it.code }.sorted()

    companion object {
        /** 필터 없음. `categories`가 생략되거나 null일 때. */
        val UNFILTERED = CategorySelection(emptySet())

        /**
         * 인자를 선택으로 정규화한다. 빈 배열은 여기서 [onEmpty]로 거절된다 — 실패를 경계에서
         * 끝내고 조회 로직에 들여보내지 않기 위해서다.
         *
         * @param onEmpty `categories`가 빈 배열일 때 던질 예외를 만든다.
         */
        fun of(
            categories: List<SpotCategory>?,
            onEmpty: () -> Throwable,
        ): CategorySelection = when {
            categories == null -> UNFILTERED
            categories.isEmpty() -> throw onEmpty()
            else -> CategorySelection(categories.toSet())
        }

        /** 이미 확정된 집합으로부터. */
        fun ofValues(values: Set<SpotCategory>): CategorySelection = CategorySelection(values)
    }
}
