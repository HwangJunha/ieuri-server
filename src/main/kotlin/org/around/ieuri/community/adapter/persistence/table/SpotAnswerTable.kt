package org.around.ieuri.community.adapter.persistence.table

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.trim
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object SpotAnswerTable : Table("community.community_spot_answer") {
    val id = long("id").autoIncrement()
    val questionId = long("question_id")
        .references(SpotQuestionTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_community_spot_answer_question")
    val catalogId = long("catalog_id")
    val authorId = long("author_id").index("idx_community_spot_answer_author")
    val body = text("body")
    val hidden = bool("hidden").default(false)
    val deletedAt = timestampWithTimeZone("deleted_at").nullable()
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    val updatedAt = timestampWithTimeZone("updated_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_community_spot_answer_question", false, questionId, createdAt, id)
        check("ck_community_spot_answer_body_not_blank") { body.trim() neq "" }
    }
}
