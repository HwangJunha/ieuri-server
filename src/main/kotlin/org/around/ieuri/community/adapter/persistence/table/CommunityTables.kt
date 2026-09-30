package org.around.ieuri.community.adapter.persistence.table

import org.around.ieuri.common.persistence.geography
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.between
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.trim
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

// community 스키마(V1__baseline.sql 이후). 이름·제약·인덱스는 마이그레이션과 같게 둔다 — 기동 검증(ExposedSchemaValidator)이 비교한다.
// CHECK 조건식은 검증 대상이 아니다(이름만 비교). 조건의 원본은 마이그레이션이다.

object PostTable : Table("community.community_post") {
    val id = long("id").autoIncrement()
    val authorId = long("author_id").index("idx_community_post_author")
    val title = varchar("title", 100)
    val body = text("body")
    val likeCount = integer("like_count").default(0)
    val commentCount = integer("comment_count").default(0)
    val hidden = bool("hidden").default(false)
    val edited = bool("edited").default(false)
    val deletedAt = timestampWithTimeZone("deleted_at").nullable()
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    val updatedAt = timestampWithTimeZone("updated_at").databaseGenerated()
    val viewCount = integer("view_count").default(0)
    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_community_post_feed", false, createdAt, id)
        check("ck_community_post_body_not_blank") { body.trim() neq "" }
        check("ck_community_post_title_not_blank") { title.trim() neq "" }
        check("ck_community_post_like_count") { likeCount greaterEq 0 }
        check("ck_community_post_comment_count") { commentCount greaterEq 0 }
        check("ck_community_post_view_count") { viewCount greaterEq 0 }
    }
}

object PostPhotoTable : Table("community.community_post_photo") {
    val id = long("id").autoIncrement()
    val postId = long("post_id").references(PostTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_community_post_photo_post")
    val objectKey = varchar("object_key", 512)
    val position = short("position")
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_community_post_photo_position", postId, position)
        index("idx_community_post_photo_post", false, postId, position)
        check("ck_community_post_photo_position") { position.between(0.toShort(), 4.toShort()) }
    }
}

object PostTagTable : Table("community.community_post_tag") {
    val id = long("id").autoIncrement()
    val postId = long("post_id").references(PostTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_community_post_tag_post")
        .index("idx_community_post_tag_post")
    val name = varchar("name", 30)
    val normalized = varchar("normalized", 30)
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_community_post_tag", postId, normalized)
        index("idx_community_post_tag_normalized", false, normalized, postId)
        check("ck_community_post_tag_name_not_blank") { name.trim() neq "" }
        check("ck_community_post_tag_normalized_not_blank") { normalized.trim() neq "" }
    }
}

object PostChipTable : Table("community.community_post_chip") {
    val postId = long("post_id").references(PostTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_community_post_chip_post")
    val category = varchar("category", 30)
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    override val primaryKey = PrimaryKey(postId)

    init {
        index("idx_community_post_chip_category", false, category, postId)
    }
}

/** `location`은 트리거가 `map_x`·`map_y`로 채운다(`ST_MakePoint`는 (lon, lat) 순서). */
object PostSpotTable : Table("community.community_post_spot") {
    val postId = long("post_id").references(PostTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_community_post_spot_post")
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    val mapX = double("map_x")
    val mapY = double("map_y")
    val location = geography("location").databaseGenerated()
    val catalogId = long("catalog_id")
    override val primaryKey = PrimaryKey(postId)

    init {
        index("idx_community_post_spot_catalog", false, catalogId, postId)
        index("idx_community_post_spot_location", false, location)
        check("ck_community_post_spot_map_x") { mapX.between(-180.0, 180.0) }
        check("ck_community_post_spot_map_y") { mapY.between(-90.0, 90.0) }
    }
}

object CommentTable : Table("community.community_comment") {
    val id = long("id").autoIncrement()
    val postId = long("post_id").references(PostTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_community_comment_post")
    val authorId = long("author_id").index("idx_community_comment_author")
    val body = text("body")
    val hidden = bool("hidden").default(false)
    val deletedAt = timestampWithTimeZone("deleted_at").nullable()
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    val updatedAt = timestampWithTimeZone("updated_at").databaseGenerated()
    val parentCommentId = long("parent_comment_id")
        .references(id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_community_comment_parent")
        .nullable()
    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_community_comment_post", false, postId, createdAt, id)
        index("idx_community_comment_parent", false, parentCommentId, createdAt, id, filterCondition = { parentCommentId.isNotNull() })
        check("ck_community_comment_body_not_blank") { body.trim() neq "" }
        check("ck_community_comment_parent_not_self") { parentCommentId.isNull() or (parentCommentId neq id) }
    }
}

object PostLikeTable : Table("community.community_post_like") {
    val id = long("id").autoIncrement()
    val postId = long("post_id").references(PostTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_community_post_like_post")
    val memberId = long("member_id")
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_community_post_like", postId, memberId)
        index("idx_community_post_like_member", false, memberId, postId)
    }
}

object PostHideTable : Table("community.community_post_hide") {
    val id = long("id").autoIncrement()
    val memberId = long("member_id")
    val postId = long("post_id").references(PostTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_community_post_hide_post")
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_community_post_hide", memberId, postId)
        index("idx_community_post_hide_member", false, memberId, createdAt, postId)
    }
}

object BlockTable : Table("community.community_block") {
    val id = long("id").autoIncrement()
    val blockerId = long("blocker_id")
    val blockedId = long("blocked_id")
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_community_block", blockerId, blockedId)
        index("idx_community_block_blocked", false, blockedId, blockerId)
        index("idx_community_block_blocker", false, blockerId, createdAt, id)
        check("ck_community_block_not_self") { blockerId neq blockedId }
    }
}

object ReportTable : Table("community.community_report") {
    val id = long("id").autoIncrement()
    val reporterId = long("reporter_id")
    val targetType = varchar("target_type", 30)
    val targetId = long("target_id")
    val reason = varchar("reason", 30)
    val detail = text("detail").nullable()
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_community_report", reporterId, targetType, targetId)
        index("idx_community_report_target", false, targetType, targetId)
        check("ck_community_report_reason") { reason inList listOf("SPAM", "ABUSE", "INAPPROPRIATE_CONTENT", "OTHER") }
        check("ck_community_report_target_type") {
            targetType inList listOf("POST", "COMMENT", "MEMBER", "SPOT_QUESTION", "SPOT_ANSWER", "SPOT_ANSWER_REPLY")
        }
        check("ck_community_report_detail_for_other") { (reason neq "OTHER") or detail.isNotNull() }
    }
}

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

/** tourism 카탈로그의 활성 언어 복제본. tourism 이벤트로 전량 교체된다. */
object SpotCatalogLanguageTable : Table("community.community_spot_catalog_language") {
    val catalogId = long("catalog_id")
    val language = varchar("language", 2)
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    override val primaryKey = PrimaryKey(catalogId, language, name = "pk_community_spot_catalog_language")

    init {
        check("ck_community_spot_catalog_language_language") { language inList listOf("EN", "KO", "JA", "ZH") }
    }
}

object ConversationTable : Table("community.community_conversation") {
    val id = long("id").autoIncrement()
    val memberLow = long("member_low")
    val memberHigh = long("member_high")
    val lastMessageAt = timestampWithTimeZone("last_message_at").databaseGenerated()
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    val lastReadMessageIdLow = long("last_read_message_id_low").nullable()
    val lastReadMessageIdHigh = long("last_read_message_id_high").nullable()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_community_conversation_pair", memberLow, memberHigh)
        index("idx_community_conversation_low", false, memberLow, lastMessageAt, id)
        index("idx_community_conversation_high", false, memberHigh, lastMessageAt, id)
        check("ck_community_conversation_members") { memberLow less memberHigh }
    }
}

object MessageTable : Table("community.community_message") {
    val id = long("id").autoIncrement()
    val conversationId = long("conversation_id")
        .references(ConversationTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_community_message_conversation")
    val senderId = long("sender_id").index("idx_community_message_sender")
    val bodyEncrypted = text("body_encrypted")
    val photoObjectKey = varchar("photo_object_key", 512).nullable()
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated().index("idx_community_message_created")
    val deletedAt = timestampWithTimeZone("deleted_at").nullable()
    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_community_message_conversation", false, conversationId, createdAt, id, filterCondition = { deletedAt.isNull() })
        index("idx_community_message_unread", false, conversationId, id, senderId, filterCondition = { deletedAt.isNull() })
    }
}

object MessageQuoteTable : Table("community.community_message_quote") {
    val id = long("id").autoIncrement()
    val messageId = long("message_id")
        .references(MessageTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_community_message_quote_message")
        .index("idx_community_message_quote_message")
    val quoteType = varchar("quote_type", 30)
    val targetId = long("target_id")
    val titleSnapshot = varchar("title_snapshot", 255)
    val primaryExcerptEncrypted = text("primary_excerpt_encrypted")
    val secondaryExcerptEncrypted = text("secondary_excerpt_encrypted").nullable()
    val contextId = long("context_id").nullable()
    val parentId = long("parent_id").nullable()
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)

    init {
        check("ck_community_message_quote_type") { quoteType inList listOf("SPOT_ANSWER") }
    }
}

object MessageReportTable : Table("community.community_message_report") {
    val id = long("id").autoIncrement()
    val reporterId = long("reporter_id")
    val conversationId = long("conversation_id")
        .references(ConversationTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_community_message_report_conversation")
    val reason = varchar("reason", 30)
    val detail = text("detail").nullable()
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)
}

object MessageReportItemTable : Table("community.community_message_report_item") {
    val id = long("id").autoIncrement()
    val reportId = long("report_id")
        .references(MessageReportTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_community_message_report_item_report")
        .index("idx_community_message_report_item_report")
    val messageId = long("message_id")
        .references(MessageTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_community_message_report_item_message")
    val reporterId = long("reporter_id")
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_community_message_report_item", reporterId, messageId)
    }
}

/** 기동 검증 대상. 새 테이블을 선언하면 여기에도 넣는다. */
val communityTables: List<Table> = listOf(
    PostTable, PostPhotoTable, PostTagTable, PostChipTable, PostSpotTable, CommentTable, PostLikeTable, PostHideTable,
    BlockTable, ReportTable, SpotQuestionTable, SpotAnswerTable, SpotAnswerReplyTable, SpotQaPhotoTable,
    SpotCatalogLanguageTable, ConversationTable, MessageTable, MessageQuoteTable, MessageReportTable, MessageReportItemTable,
)
