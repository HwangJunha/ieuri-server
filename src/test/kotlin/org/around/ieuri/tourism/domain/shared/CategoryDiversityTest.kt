package org.around.ieuri.tourism.domain.shared

import kotlin.test.Test
import kotlin.test.assertEquals

/** SPEC-0035 — 카테고리 반복 감점. 행을 숨기지 않고 순서만 바꾼다. */
class CategoryDiversityTest {

    private val shopping = SpotCategory("SHOPPING")
    private val restaurant = SpotCategory("RESTAURANT")
    private val park = SpotCategory("TOURIST_SPOT")

    @Test
    fun `같은 카테고리가 반복되면 감점돼 다른 카테고리가 사이에 들어온다`() {
        // 옷가게 셋이 점수가 더 높아도 두 번째부터는 0.1씩 깎여 식당·공원이 앞으로 온다.
        val items = listOf(
            RankedSpot(1, shopping, 0.90),
            RankedSpot(2, shopping, 0.89),
            RankedSpot(3, shopping, 0.88),
            RankedSpot(4, restaurant, 0.85),
            RankedSpot(5, park, 0.84),
        )
        assertEquals(listOf(1L, 4L, 5L, 2L, 3L), CategoryDiversity(penalty = 0.1).order(items))
    }

    @Test
    fun `감점이 0이면 순수 점수순이다`() {
        val items = listOf(RankedSpot(1, shopping, 0.5), RankedSpot(2, shopping, 0.9), RankedSpot(3, park, 0.7))
        assertEquals(listOf(2L, 3L, 1L), CategoryDiversity(penalty = 0.0).order(items))
    }

    @Test
    fun `점수 차가 감점보다 크면 같은 카테고리도 연달아 나온다`() {
        val items = listOf(RankedSpot(1, shopping, 0.9), RankedSpot(2, shopping, 0.85), RankedSpot(3, park, 0.3))
        assertEquals(listOf(1L, 2L, 3L), CategoryDiversity(penalty = 0.1).order(items))
    }

    @Test
    fun `입력 전부가 출력에 있고 동점은 id 오름차순이다`() {
        val items = (1L..6L).map { RankedSpot(it, if (it % 2 == 0L) shopping else park, 0.5) }
        val ordered = CategoryDiversity(penalty = 0.1).order(items)
        assertEquals(items.map { it.id }.toSet(), ordered.toSet())
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 6L), ordered)
    }

    @Test
    fun `입력 순서와 무관하게 같은 결과다`() {
        val items = listOf(RankedSpot(1, shopping, 0.9), RankedSpot(2, shopping, 0.89), RankedSpot(3, park, 0.85))
        assertEquals(CategoryDiversity(0.1).order(items), CategoryDiversity(0.1).order(items.reversed()))
    }
}
