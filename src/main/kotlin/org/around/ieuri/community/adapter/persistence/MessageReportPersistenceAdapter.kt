package org.around.ieuri.community.adapter.persistence

import org.around.ieuri.community.adapter.persistence.mapper.writeTo
import org.around.ieuri.community.adapter.persistence.table.MessageReportItemTable
import org.around.ieuri.community.adapter.persistence.table.MessageReportTable
import org.around.ieuri.community.application.messagereport.required.MessageReportRepository
import org.around.ieuri.community.domain.messagereport.MessageReport
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.stereotype.Repository

@Repository
class MessageReportPersistenceAdapter : MessageReportRepository {

    /** 동시에 같은 신고자가 먼저 넣은 항목은 삽입되지 않는다 — 하나라도 빠지면 false. */
    override fun insert(report: MessageReport): Boolean {
        val alreadyReported = MessageReportItemTable.select(MessageReportItemTable.id)
            .where { (MessageReportItemTable.reporterId eq report.reporterId) and (MessageReportItemTable.messageId inList report.messageIds) }
            .limit(1).any()
        if (alreadyReported) return false

        val reportId = MessageReportTable.insertReturning(listOf(MessageReportTable.id)) { report.writeTo(it) }.single()[MessageReportTable.id]
        return report.messageIds.all { messageId ->
            MessageReportItemTable.insertIgnore {
                it[MessageReportItemTable.reportId] = reportId
                it[MessageReportItemTable.messageId] = messageId
                it[reporterId] = report.reporterId
            }.insertedCount > 0
        }
    }
}
