package org.around.ieuri.tourism.adapter.persistence.query

import org.around.ieuri.common.persistence.cosineDistance
import org.around.ieuri.common.persistence.vectorAvg
import org.around.ieuri.tourism.adapter.persistence.table.SpotCatalogTranslationTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotEmbeddingTable
import org.around.ieuri.tourism.application.recommendation.required.TasteSimilarity
import org.around.ieuri.tourism.application.recommendation.required.TasteSimilarityQueryPort
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.anyFrom
import org.jetbrains.exposed.v1.core.doubleLiteral
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.minus
import org.jetbrains.exposed.v1.core.wrapAsExpression
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.stereotype.Component

/**
 * 취향 유사도 조회 어댑터(SPEC-0035). 회원이 반응한 장소 벡터의 평균과 후보의 코사인 유사도만 돌려준다 — 벡터는 DB 밖으로 나오지 않는다.
 * - 반응한 장소가 없으면(또는 임베딩이 없으면) 그 유사도는 null이다.
 */
@Component
class TasteSimilarityQueryAdapter : TasteSimilarityQueryPort {

    override fun findSimilarities(
        likedCatalogIds: Set<Long>,
        dislikedCatalogIds: Set<Long>,
        candidateCatalogIds: Collection<Long>,
        language: DomainLocale,
        model: String,
    ): Map<Long, TasteSimilarity> {
        if (candidateCatalogIds.isEmpty()) return emptyMap()
        val like = similarityTo(likedCatalogIds, language, model)
        val dislike = similarityTo(dislikedCatalogIds, language, model)

        return embeddings()
            .select(listOfNotNull(SpotCatalogTranslationTable.catalogId, like, dislike))
            .where { inScope(candidateCatalogIds, language, model) }
            .associate {
                it[SpotCatalogTranslationTable.catalogId] to TasteSimilarity(like = like?.let { similarity -> it[similarity] }, dislike = dislike?.let { similarity -> it[similarity] })
            }
    }

    /** `1 - (후보 벡터 <=> 반응 장소 벡터 평균)`. 반응 장소가 없으면 식이 없다(null). 평균은 상관 없는 서브쿼리라 한 번만 계산된다. */
    private fun similarityTo(catalogIds: Set<Long>, language: DomainLocale, model: String): ExpressionWithColumnType<Double?>? {
        if (catalogIds.isEmpty()) return null
        val reactedEmbedding = SpotEmbeddingTable.alias("reacted_embedding")
        val reactedTranslation = SpotCatalogTranslationTable.alias("reacted_translation")
        val centroid: Expression<String?> = wrapAsExpression(
            reactedEmbedding.join(reactedTranslation, JoinType.INNER, reactedEmbedding[SpotEmbeddingTable.translationId], reactedTranslation[SpotCatalogTranslationTable.id])
                .select(vectorAvg(reactedEmbedding[SpotEmbeddingTable.embedding]))
                .where {
                    (reactedEmbedding[SpotEmbeddingTable.model] eq model) and (reactedTranslation[SpotCatalogTranslationTable.language] eq language.name) and
                        (reactedTranslation[SpotCatalogTranslationTable.catalogId] eq anyFrom(catalogIds.toList()))
                },
        )
        @Suppress("UNCHECKED_CAST")
        return (doubleLiteral(1.0) - (SpotEmbeddingTable.embedding cosineDistance (centroid as Expression<String>))) as ExpressionWithColumnType<Double?>
    }

    private fun embeddings() = SpotEmbeddingTable.join(SpotCatalogTranslationTable, JoinType.INNER, SpotEmbeddingTable.translationId, SpotCatalogTranslationTable.id)

    private fun inScope(catalogIds: Collection<Long>, language: DomainLocale, model: String) = (SpotEmbeddingTable.model eq model) and
        (SpotCatalogTranslationTable.language eq language.name) and (SpotCatalogTranslationTable.catalogId eq anyFrom(catalogIds.toList()))
}
