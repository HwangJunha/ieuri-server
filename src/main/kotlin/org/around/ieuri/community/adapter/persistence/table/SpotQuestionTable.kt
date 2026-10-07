package org.around.ieuri.community.adapter.persistence.table

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.trim
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object SpotQuestionTable : Table("community.community_spot_question") {
    val id = long("id").autoIncrement()
    val catalogId = long("catalog_id")
    val authorId = long("author_id").index("idx_community_spot_question_author")
    val body = text("body")
    val resolved = bool("resolved").default(false)
    val answerCount = integer("answer_count").default(0)
    val hidden = bool("hidden").default(false)
    val deletedAt = timestampWithTimeZone("deleted_at").nullable()
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    val updatedAt = timestampWithTimeZone("updated_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_community_spot_question_catalog", false, catalogId, resolved, createdAt, id)
        check("ck_community_spot_question_answer_count") { answerCount greaterEq 0 }
        check("ck_community_spot_question_body_not_blank") { body.trim() neq "" }
    }
}
