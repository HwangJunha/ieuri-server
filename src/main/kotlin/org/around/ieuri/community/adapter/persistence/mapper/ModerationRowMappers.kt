package org.around.ieuri.community.adapter.persistence.mapper

import org.around.ieuri.community.adapter.persistence.table.BlockTable
import org.around.ieuri.community.adapter.persistence.table.MessageReportTable
import org.around.ieuri.community.adapter.persistence.table.ReportTable
import org.around.ieuri.community.domain.block.Block
import org.around.ieuri.community.domain.messagereport.MessageReport
import org.around.ieuri.community.domain.report.Report
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder

/** 차단 한 행. blocker가 차단한 쪽이다. */
fun Block.writeTo(row: UpdateBuilder<*>) {
    row[BlockTable.blockerId] = blockerId
    row[BlockTable.blockedId] = blockedId
}

/** 신고 한 행. 대상 종류·사유는 enum 이름으로 쓴다. */
fun Report.writeTo(row: UpdateBuilder<*>) {
    row[ReportTable.reporterId] = reporterId
    row[ReportTable.targetType] = targetType.name
    row[ReportTable.targetId] = targetId
    row[ReportTable.reason] = reason.name
    row[ReportTable.detail] = detail
}

/** 메시지 신고 머리 행. 신고한 메시지 항목은 저장소가 따로 넣는다. */
fun MessageReport.writeTo(row: UpdateBuilder<*>) {
    row[MessageReportTable.reporterId] = reporterId
    row[MessageReportTable.conversationId] = conversationId
    row[MessageReportTable.reason] = reason.name
    row[MessageReportTable.detail] = detail
}
