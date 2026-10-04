package org.around.ieuri.community.adapter.persistence

import org.around.ieuri.community.adapter.persistence.table.CommentTable
import org.around.ieuri.community.adapter.persistence.table.PostTable
import org.around.ieuri.community.adapter.persistence.table.ReportTable
import org.around.ieuri.community.adapter.persistence.table.SpotAnswerReplyTable
import org.around.ieuri.community.adapter.persistence.table.SpotAnswerTable
import org.around.ieuri.community.adapter.persistence.table.SpotQuestionTable
import org.around.ieuri.community.application.report.required.ReportRepository
import org.around.ieuri.community.application.report.required.ReportTargetRepository
import org.around.ieuri.community.domain.report.Report
import org.around.ieuri.community.domain.report.ReportTargetType
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.minus
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.updateReturning
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
class ReportPersistenceAdapter : ReportRepository {

    override fun add(report: Report): Boolean = ReportTable.insertIgnore {
        it[reporterId] = report.reporterId
        it[targetType] = report.targetType.name
        it[targetId] = report.targetId
        it[reason] = report.reason.name
        it[detail] = report.detail
    }.insertedCount > 0

    /** 같은 사람의 반복 신고는 유일 제약이 막으므로 행 수가 곧 서로 다른 신고자 수다. */
    override fun countDistinctReporters(targetType: ReportTargetType, targetId: Long): Int = ReportTable.select(ReportTable.id)
        .where { (ReportTable.targetType eq targetType.name) and (ReportTable.targetId eq targetId) }
        .count().toInt()
}
