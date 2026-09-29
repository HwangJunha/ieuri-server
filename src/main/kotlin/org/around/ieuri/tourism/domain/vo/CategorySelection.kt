package org.around.ieuri.tourism.domain.vo

/**
 * 조회에 적용할 카테고리 선택(SPEC-0020). 비어 있으면 "필터 없음"이며 전 카테고리가 대상이다.
 * - 선택된 값들은 OR로 결합된다.
 * - 알 수 없는 코드를 거절하지 않는다 — 결과가 비어 있을 뿐이다.
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
         * 인자를 선택으로 정규화한다.
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
