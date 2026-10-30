package org.around.ieuri.community.adapter.persistence.table

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

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
