package org.around.ieuri.community.adapter.persistence.table

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

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
