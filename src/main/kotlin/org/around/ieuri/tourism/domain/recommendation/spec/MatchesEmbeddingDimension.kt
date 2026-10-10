package org.around.ieuri.tourism.domain.recommendation.spec

import org.around.ieuri.tourism.domain.shared.Specification

/** 제공자가 돌려준 벡터가 모델의 차원과 같은가(SPEC-0035 S2). 다르면 저장하지 않는다 — 컬럼 차원과 어긋난 벡터는 거리 계산을 망친다. */
class MatchesEmbeddingDimension(private val dimension: Int) : Specification<FloatArray> {
    override fun isSatisfiedBy(candidate: FloatArray): Boolean = candidate.size == dimension
}
