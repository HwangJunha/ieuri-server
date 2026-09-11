package org.around.ieuri.tourism.domain.strategy

import org.around.ieuri.tourism.domain.vo.SpotCategory
import kotlin.math.sqrt

/** 유사도 계산 입력. [vector]가 null이면 벡터가 없는 장소다. 배열을 들고 있어 data class가 아니다. */
class SimilarityInput(
    val category: SpotCategory,
    val vector: FloatArray? = null,
)

/**
 * 장소 간 거리 D(SPEC-0035 R14). 둘 다 벡터가 있으면 코사인 거리(1 − cos), 하나라도 없으면
 * 카테고리 동일 여부로 대체한다. 값은 설정에서 온다.
 */
class SpotSimilarity(
    private val sameCategoryDistance: Double,
    private val differentCategoryDistance: Double,
) {
    init {
        require(sameCategoryDistance >= 0 && differentCategoryDistance >= 0) { "대체 거리는 음수일 수 없습니다." }
    }

    fun distance(a: SimilarityInput, b: SimilarityInput): Double {
        val va = a.vector
        val vb = b.vector
        if (va != null && vb != null && va.size == vb.size && va.isNotEmpty()) {
            return (1.0 - cosine(va, vb)).coerceIn(0.0, 2.0)
        }
        return if (a.category == b.category) sameCategoryDistance else differentCategoryDistance
    }

    private fun cosine(a: FloatArray, b: FloatArray): Double {
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            dot += a[i].toDouble() * b[i]
            na += a[i].toDouble() * a[i]
            nb += b[i].toDouble() * b[i]
        }
        if (na == 0.0 || nb == 0.0) return 0.0
        return dot / (sqrt(na) * sqrt(nb))
    }
}
