package org.around.ieuri.community.adapter.persistence.table

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

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
