package org.around.ieuri.community.adapter.persistence.table

import org.jetbrains.exposed.v1.core.Table
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
