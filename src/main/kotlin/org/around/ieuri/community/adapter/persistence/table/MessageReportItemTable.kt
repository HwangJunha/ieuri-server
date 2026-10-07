package org.around.ieuri.community.adapter.persistence.table

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

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
