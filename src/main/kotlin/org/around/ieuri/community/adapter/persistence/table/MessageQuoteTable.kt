package org.around.ieuri.community.adapter.persistence.table

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

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
