package org.around.ieuri.tourism.domain.strategy

import kotlin.math.exp
import kotlin.math.sqrt

/** 재배열 대상 1건. [quality]는 추천 점수 q이며 커널의 대각(q²)과 비대각(q_i·q_j)을 만든다. */
data class DppItem(
    val id: Long,
    val quality: Double,
)

/**
 * DPP 그리디 재배열(SPEC-0035 R13, Wilhelm 외 2018). 커널 `L_ii = q_i²`,
 * `L_ij = α·q_i·q_j·exp(−D_ij / 2σ²)`. 창 k개씩 행렬식이 최대가 되도록 하나씩 고르고, 창이 차면
 * 남은 후보로 다음 창을 만든다. 순서는 창 순 → 창 안 선택 순이다.
 *
 * 그리디는 Chen 외 2018의 증분 Cholesky(fast greedy MAP)로, 나이브 행렬식 그리디와 같은 순열을
 * 낸다(`DppRerankerTest`가 고정). **동점은 id 오름차순** — 같은 입력은 같은 순서다(AC-13.3).
 * α = 0이면 비대각이 사라져 점수 내림차순과 같다(AC-13.2).
 */
class DppReranker(
    private val alpha: Double,
    private val sigma: Double,
    private val window: Int,
) {
    init {
        require(alpha >= 0) { "α는 음수일 수 없습니다." }
        require(sigma > 0) { "σ는 양수여야 합니다." }
        require(window >= 1) { "창 크기는 1 이상이어야 합니다." }
    }

    /** 커널의 비대각 원소. 학습 배치도 같은 식을 써야 하므로 공개한다. */
    fun kernelOffDiagonal(qi: Double, qj: Double, distance: Double): Double =
        alpha * qi * qj * exp(-distance / (2.0 * sigma * sigma))

    /**
     * [items] 전부를 재배열한 id 순열. [distance]는 두 항목 사이의 D이며 대칭이어야 한다.
     * 입력 순서에 의존하지 않도록 먼저 id 순으로 정렬한다.
     */
    fun rerank(items: List<DppItem>, distance: (DppItem, DppItem) -> Double): List<Long> {
        val remaining = items.sortedBy { it.id }.toMutableList()
        val order = ArrayList<Long>(items.size)
        while (remaining.isNotEmpty()) {
            val chosen = selectWindow(remaining, distance)
            order += chosen.map { it.id }
            remaining -= chosen.toSet()
        }
        return order
    }

    /**
     * 창 하나를 채운다. 후보 i의 `d2[i]`는 "i를 추가했을 때 행렬식이 몇 배가 되는가"(Schur 보수)이고,
     * 선택할 때마다 나머지 후보의 d2를 Cholesky 한 열로 갱신한다.
     */
    private fun selectWindow(candidates: List<DppItem>, distance: (DppItem, DppItem) -> Double): List<DppItem> {
        val n = candidates.size
        val k = minOf(window, n)
        val d2 = DoubleArray(n) { candidates[it].quality * candidates[it].quality }
        // c[i]는 후보 i의 Cholesky 행(선택된 항목 수만큼 채워진다).
        val c = Array(n) { DoubleArray(k) }
        val selected = BooleanArray(n)
        val chosen = ArrayList<DppItem>(k)

        repeat(k) { step ->
            var best = -1
            for (i in 0 until n) {
                if (selected[i]) continue
                // 동점은 id 오름차순 — candidates가 id 순이라 먼저 만난 후보를 유지하면 된다.
                if (best < 0 || d2[i] > d2[best]) best = i
            }
            selected[best] = true
            chosen += candidates[best]

            val pivot = sqrt(d2[best].coerceAtLeast(0.0))
            for (i in 0 until n) {
                if (selected[i]) continue
                val lij = kernelOffDiagonal(candidates[best].quality, candidates[i].quality, distance(candidates[best], candidates[i]))
                var dot = 0.0
                for (t in 0 until step) dot += c[best][t] * c[i][t]
                val e = if (pivot > 0.0) (lij - dot) / pivot else 0.0
                c[i][step] = e
                d2[i] = (d2[i] - e * e).coerceAtLeast(0.0)
            }
        }
        return chosen
    }
}
