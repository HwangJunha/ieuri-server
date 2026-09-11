package org.around.ieuri.tourism.domain

import org.around.ieuri.tourism.domain.strategy.DppItem
import org.around.ieuri.tourism.domain.strategy.DppReranker
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** SPEC-0035 R13·R15·R16 — DPP 그리디 재배열. 증분 Cholesky가 나이브 행렬식 그리디와 같은 순열을 낸다. */
class DppRerankerTest {

    @Test
    fun `점수가 같고 닮은 A·B와 덜 닮은 C가 있으면 두 번째는 C다 - AC-13_1`() {
        val reranker = DppReranker(alpha = 1.0, sigma = 0.5, window = 3)
        val a = DppItem(1, 0.9)
        val b = DppItem(2, 0.9)
        val c = DppItem(3, 0.9)
        val order = reranker.rerank(listOf(a, b, c)) { x, y ->
            if (setOf(x.id, y.id) == setOf(1L, 2L)) 0.01 else 1.0
        }
        assertEquals(1L, order[0])
        assertEquals(3L, order[1])
        assertEquals(2L, order[2])
    }

    @Test
    fun `알파가 0이면 점수 내림차순과 같다 - AC-13_2`() {
        val reranker = DppReranker(alpha = 0.0, sigma = 0.5, window = 3)
        val items = listOf(DppItem(1, 0.2), DppItem(2, 0.9), DppItem(3, 0.5), DppItem(4, 0.9))
        val order = reranker.rerank(items) { _, _ -> 0.0 }
        // 동점(2·4)은 id 오름차순.
        assertEquals(listOf(2L, 4L, 3L, 1L), order)
    }

    @Test
    fun `같은 입력은 입력 순서와 무관하게 같은 순서를 낸다 - AC-13_3`() {
        val reranker = DppReranker(alpha = 1.0, sigma = 0.5, window = 4)
        val items = randomItems(seed = 7, count = 30)
        val distances = randomDistances(seed = 8, ids = items.map { it.id })
        val first = reranker.rerank(items) { a, b -> distances.getValue(a.id to b.id) }
        val second = reranker.rerank(items.shuffled(Random(3))) { a, b -> distances.getValue(a.id to b.id) }
        assertEquals(first, second)
    }

    @Test
    fun `창이 2이고 후보가 5면 2·2·1로 전부 배치된다 - AC-13_4`() {
        val reranker = DppReranker(alpha = 1.0, sigma = 0.5, window = 2)
        val items = (1L..5L).map { DppItem(it, 0.5 + it * 0.05) }
        val order = reranker.rerank(items) { a, b -> if (a.id == b.id) 0.0 else 1.0 }
        assertEquals(5, order.size)
        assertEquals(items.map { it.id }.toSet(), order.toSet())
        // 첫 창은 점수 최고(5), 다음 창은 남은 것 중 최고(3)로 시작한다 — 창 순 → 창 안 선택 순.
        assertEquals(5L, order[0])
        assertEquals(3L, order[2])
        assertEquals(1L, order[4])
    }

    @Test
    fun `커널이 잘 조건화되면 증분 Cholesky 그리디는 나이브 행렬식 그리디와 같은 순열을 낸다`() {
        // σ가 작으면 비대각이 빠르게 0에 가까워져 동점이 생기지 않는다 — 순열이 그대로 일치해야 한다.
        val reranker = DppReranker(alpha = 1.0, sigma = 0.3, window = 5)
        repeat(5) { seed ->
            val items = randomItems(seed = seed, count = 18)
            val distances = randomDistances(seed = seed + 100, ids = items.map { it.id })
            val fast = reranker.rerank(items) { a, b -> distances.getValue(a.id to b.id) }
            val naive = naiveGreedy(reranker, items, window = 5) { a, b -> distances.getValue(a.id to b.id) }
            assertEquals(naive, fast, "seed=$seed")
        }
    }

    @Test
    fun `커널이 특이에 가까워도 매 선택은 행렬식을 최대로 만드는 후보다`() {
        // σ가 크면 항목들이 강하게 상관돼 창 후반의 행렬식이 0에 가깝다. 부동소수 잡음으로 동점의 순서는
        // 갈릴 수 있지만, 고른 후보의 행렬식은 나이브 최대와 상대 오차 안에서 같아야 한다.
        val reranker = DppReranker(alpha = 1.0, sigma = 0.7, window = 5)
        repeat(5) { seed ->
            val items = randomItems(seed = seed, count = 18)
            val distances = randomDistances(seed = seed + 100, ids = items.map { it.id })
            val distance = { a: DppItem, b: DppItem -> distances.getValue(a.id to b.id) }
            val fast = reranker.rerank(items, distance)
            assertGreedyOptimal(reranker, items, fast, window = 5, distance = distance, label = "seed=$seed")
        }
    }

    /** 창마다 "추가 시 행렬식 최대"인 후보를 고르는 나이브 구현. 동점은 id 오름차순. */
    private fun naiveGreedy(
        reranker: DppReranker,
        items: List<DppItem>,
        window: Int,
        distance: (DppItem, DppItem) -> Double,
    ): List<Long> {
        val remaining = items.sortedBy { it.id }.toMutableList()
        val order = mutableListOf<Long>()
        while (remaining.isNotEmpty()) {
            val chosen = mutableListOf<DppItem>()
            repeat(minOf(window, remaining.size)) {
                var best: DppItem? = null
                var bestDet = Double.NEGATIVE_INFINITY
                for (candidate in remaining) {
                    if (candidate in chosen) continue
                    val det = determinant(kernelMatrix(reranker, chosen + candidate, distance))
                    if (det > bestDet) {
                        bestDet = det
                        best = candidate
                    }
                }
                chosen += best!!
            }
            order += chosen.map { it.id }
            remaining -= chosen.toSet()
        }
        return order
    }

    /** [order]의 각 창·각 단계에서 고른 후보의 행렬식이 그 시점 최대 행렬식과 (상대 오차 안에서) 같은지. */
    private fun assertGreedyOptimal(
        reranker: DppReranker,
        items: List<DppItem>,
        order: List<Long>,
        window: Int,
        distance: (DppItem, DppItem) -> Double,
        label: String,
    ) {
        val byId = items.associateBy { it.id }
        val remaining = items.toMutableList()
        var index = 0
        while (index < order.size) {
            val chosen = mutableListOf<DppItem>()
            repeat(minOf(window, remaining.size)) {
                val picked = byId.getValue(order[index++])
                val pickedDet = determinant(kernelMatrix(reranker, chosen + picked, distance))
                val bestDet = remaining.filter { it !in chosen }
                    .maxOf { determinant(kernelMatrix(reranker, chosen + it, distance)) }
                // 최대 행렬식이 소거 잡음 수준(≤1e-6)이면 남은 후보가 전부 기존 선택의 생성 공간 안에 있다는
                // 뜻이라 어느 것을 골라도 같다 — 그 단계는 판정하지 않는다.
                if (bestDet > 1e-6) {
                    assertTrue(pickedDet >= bestDet * (1 - 1e-6), "$label step=$index picked=$pickedDet best=$bestDet")
                }
                chosen += picked
            }
            remaining -= chosen.toSet()
        }
    }

    private fun kernelMatrix(reranker: DppReranker, set: List<DppItem>, distance: (DppItem, DppItem) -> Double) =
        set.map { r ->
            set.map { c ->
                if (r.id == c.id) r.quality * r.quality else reranker.kernelOffDiagonal(r.quality, c.quality, distance(r, c))
            }
        }

    private fun determinant(m: List<List<Double>>): Double {
        val n = m.size
        val a = Array(n) { i -> DoubleArray(n) { j -> m[i][j] } }
        var det = 1.0
        for (col in 0 until n) {
            var pivot = col
            for (row in col + 1 until n) if (abs(a[row][col]) > abs(a[pivot][col])) pivot = row
            if (abs(a[pivot][col]) < 1e-300) return 0.0
            if (pivot != col) {
                val tmp = a[pivot]; a[pivot] = a[col]; a[col] = tmp
                det = -det
            }
            det *= a[col][col]
            for (row in col + 1 until n) {
                val factor = a[row][col] / a[col][col]
                for (k in col until n) a[row][k] -= factor * a[col][k]
            }
        }
        return det
    }

    private fun randomItems(seed: Int, count: Int): List<DppItem> {
        val random = Random(seed)
        return (1L..count).map { DppItem(it, 0.1 + random.nextDouble() * 0.9) }
    }

    private fun randomDistances(seed: Int, ids: List<Long>): Map<Pair<Long, Long>, Double> {
        val random = Random(seed)
        val out = HashMap<Pair<Long, Long>, Double>()
        for (i in ids) for (j in ids) {
            if (i < j) {
                val d = random.nextDouble() * 2.0
                out[i to j] = d
                out[j to i] = d
            } else if (i == j) {
                out[i to j] = 0.0
            }
        }
        return out
    }
}
