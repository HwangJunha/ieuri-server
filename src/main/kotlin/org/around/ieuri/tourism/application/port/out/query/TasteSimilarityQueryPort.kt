package org.around.ieuri.tourism.application.port.out.query

import org.around.ieuri.tourism.domain.vo.DomainLocale

/**
 * 취향 유사도 조회 포트(SPEC-0035 S2). **생 SQL 어댑터가 뒤에 있다** — pgvector의 평균·코사인 연산자를
 * QueryDSL로 표현할 수 없다. 회원의 좋아요·싫어요 장소 벡터 평균과 후보 벡터의 코사인 유사도를
 * DB 안에서 구하고 숫자만 돌려준다.
 */
interface TasteSimilarityQueryPort {
    /**
     * [candidateCatalogIds] 각각의 좋아요 평균·싫어요 평균과의 코사인 유사도(−1~1). **[language]의
     * 번역 행 벡터**만 쓰고, [model]과 다른 모델의 벡터는 없는 것으로 본다.
     *
     * 벡터가 없는 후보는 결과에 키가 없다. 좋아요 장소에 벡터가 하나도 없으면 [TasteSimilarity.like]가
     * null이다 — 호출자는 그 후보의 취향 요인을 없는 것으로 다룬다.
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
