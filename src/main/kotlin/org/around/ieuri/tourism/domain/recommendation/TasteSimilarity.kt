package org.around.ieuri.tourism.domain.recommendation

/** 후보 하나의 코사인 유사도(−1~1). [like]는 좋아요 장소에 벡터가 없으면, [dislike]는 싫어요 장소가 없거나 벡터가 없으면 null. */
data class TasteSimilarity(
    val like: Double?,
    val dislike: Double?,
)
