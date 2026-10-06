package org.around.ieuri.tourism.domain.shared

import org.around.ieuri.tourism.domain.shared.SpotCategory

/** 재배열 대상 1건. */
data class RankedSpot(
    val id: Long,
    val category: SpotCategory,
    val score: Double,
)

/**
 * 카테고리 반복 감점(SPEC-0035). 같은 카테고리의 n번째 장소는 점수에서 `penalty × (n − 1)`을 깎는다.
 * - 행을 숨기지 않는다 — 순서만 바꾸며 입력 전부가 출력에 있다. 동점은 id 오름차순.
 */
class CategoryDiversity(private val penalty: Double) {
    init {
        require(penalty >= 0) { "반복 감점은 음수일 수 없습니다." }
    }

    fun order(items: List<RankedSpot>): List<Long> {
        val rankInCategory = HashMap<Long, Int>()
        items.groupBy { it.category }.values.forEach { group ->
            group.sortedWith(compareByDescending<RankedSpot> { it.score }.thenBy { it.id })
                .forEachIndexed { index, item -> rankInCategory[item.id] = index }
        }
        return items
            .map { it to (it.score - penalty * rankInCategory.getValue(it.id)) }
            .sortedWith(compareByDescending<Pair<RankedSpot, Double>> { it.second }.thenBy { it.first.id })
            .map { it.first.id }
    }
}
