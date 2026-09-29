package org.around.ieuri.tourism.application.port.out.query

import org.around.ieuri.tourism.domain.vo.DomainLocale

/** 취향 유사도 조회 포트(SPEC-0035). 좋아요·싫어요 벡터 평균과 후보 벡터의 코사인 유사도를 DB에서 구한다. */
interface TasteSimilarityQueryPort {
    /**
     * 후보별 코사인 유사도(−1~1). [language]의 [model] 벡터만 쓴다.
     * - 벡터가 없는 후보는 키가 없다. 좋아요 장소에 벡터가 없으면 [TasteSimilarity.like]가 null이다.
     */
    fun findSimilarities(
        likedCatalogIds: Set<Long>,
        dislikedCatalogIds: Set<Long>,
        candidateCatalogIds: Collection<Long>,
        language: DomainLocale,
        model: String,
    ): Map<Long, TasteSimilarity>
}

/** 후보 하나의 유사도. [dislike]는 싫어요 장소가 없거나 벡터가 없으면 null. */
data class TasteSimilarity(
    val like: Double?,
    val dislike: Double?,
)
