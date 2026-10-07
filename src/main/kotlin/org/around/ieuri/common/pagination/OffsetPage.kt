package org.around.ieuri.common.pagination

/**
 * 오프셋(페이지 번호) 페이지네이션의 공통 읽기 모델. [page]는 1부터다.
 * - [hasNext]는 [totalCount]와 같은 조건에서 도출한다 — [of]로 만들면 둘이 모순되지 않는다.
 */
data class OffsetPage<T>(
    val items: List<T>,
    val page: Int,
    val hasNext: Boolean,
    val totalCount: Int,
) {
    companion object {
        /** [size]개씩 자른 [page]번째 페이지. 다음 페이지 여부는 전체 건수로 판정한다. */
        fun <T> of(items: List<T>, page: Int, size: Int, totalCount: Int): OffsetPage<T> = OffsetPage(
            items = items,
            page = page,
            hasNext = page.toLong() * size < totalCount.toLong(),
            totalCount = totalCount,
        )

        /** 결과가 없는 [page]번째 페이지. */
        fun <T> empty(page: Int): OffsetPage<T> = OffsetPage(items = emptyList(), page = page, hasNext = false, totalCount = 0)
    }
}
