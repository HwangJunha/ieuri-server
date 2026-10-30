package org.around.ieuri.community.adapter.persistence.table

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.between
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
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

object SpotAnswerReplyTable : Table("community.community_spot_answer_reply") {
    val id = long("id").autoIncrement()
    val answerId = long("answer_id")
        .references(SpotAnswerTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_community_spot_answer_reply_answer")
    val catalogId = long("catalog_id")
    val authorId = long("author_id").index("idx_community_spot_answer_reply_author")
    val body = text("body")
    val hidden = bool("hidden").default(false)
    val deletedAt = timestampWithTimeZone("deleted_at").nullable()
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    val updatedAt = timestampWithTimeZone("updated_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_community_spot_answer_reply_answer", false, answerId, createdAt, id)
        check("ck_community_spot_answer_reply_body_not_blank") { body.trim() neq "" }
    }
}

/** 사진의 주인은 질문·답변·답글 중 하나다(다형 참조라 FK가 없다). */
object SpotQaPhotoTable : Table("community.community_spot_qa_photo") {
    val id = long("id").autoIncrement()
    val ownerType = varchar("owner_type", 10)
    val ownerId = long("owner_id")
    val objectKey = varchar("object_key", 512)
    val position = short("position")
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_community_spot_qa_photo_position", ownerType, ownerId, position)
        index("idx_community_spot_qa_photo_owner", false, ownerType, ownerId, position)
        check("ck_community_spot_qa_photo_owner_type") { ownerType inList listOf("QUESTION", "ANSWER", "REPLY") }
        check("ck_community_spot_qa_photo_position") { position.between(0.toShort(), 4.toShort()) }
    }
}
