package org.around.ieuri.tourism.adapter.persistence

import org.around.ieuri.common.persistence.vectorParam
import org.around.ieuri.tourism.adapter.persistence.table.SpotEmbeddingTable
import org.around.ieuri.tourism.application.recommendation.required.SpotEmbeddingRepository
import org.around.ieuri.tourism.domain.recommendation.SpotEmbedding
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.upsert
import org.springframework.stereotype.Component

/** 장소 임베딩 저장(SPEC-0035). */
@Component
class SpotEmbeddingPersistenceAdapter : SpotEmbeddingRepository {

    /** 벡터 값은 캐스트가 필요해 한 행씩 upsert한다(호출당 수십 건). */
    override fun saveAll(embeddings: List<SpotEmbedding>) {
        embeddings.forEach { embedding ->
            SpotEmbeddingTable.upsert(
                SpotEmbeddingTable.translationId,
                onUpdate = {
                    it[SpotEmbeddingTable.model] = embedding.model
                    it[SpotEmbeddingTable.textHash] = embedding.sourceHash
                    it[SpotEmbeddingTable.embedding] = vectorParam(embedding.vector)
                    it[SpotEmbeddingTable.updatedAt] = CurrentTimestampWithTimeZone
                },
            ) {
                it[SpotEmbeddingTable.translationId] = embedding.translationId
                it[SpotEmbeddingTable.model] = embedding.model
                it[SpotEmbeddingTable.textHash] = embedding.sourceHash
                it[SpotEmbeddingTable.embedding] = vectorParam(embedding.vector)
            }
        }
    }
}
