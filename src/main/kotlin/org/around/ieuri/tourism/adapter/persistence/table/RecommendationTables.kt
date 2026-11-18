package org.around.ieuri.tourism.adapter.persistence.table

import org.around.ieuri.common.persistence.vector
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

/** 번역 행 하나의 임베딩. [textHash]는 임베딩한 텍스트의 md5라, 텍스트가 바뀌면 다시 대상이 된다. */
object SpotEmbeddingTable : Table("tour.spot_embedding") {
    val translationId = long("translation_id")
        .references(SpotCatalogTranslationTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "spot_embedding_translation_id_fkey")
    val model = varchar("model", 50)
    val textHash = char("text_hash", 32)
    val embedding = vector("embedding")
    val updatedAt = timestampWithTimeZone("updated_at").databaseGenerated()
    override val primaryKey = PrimaryKey(translationId)
}
