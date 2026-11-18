package org.around.ieuri.tourism.domain.recommendation

/** 제공자가 돌려준 벡터의 차원이 모델과 다르다. 저장하지 않는다. */
class EmbeddingDimensionMismatchException(translationId: Long, expected: Int, actual: Int) : IllegalArgumentException("임베딩 차원이 다릅니다: translationId=$translationId expected=$expected actual=$actual")
