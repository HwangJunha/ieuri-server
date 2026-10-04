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

/** 신고 대상 테이블들. 숨김은 보이는(숨겨지지도 삭제되지도 않은) 행만 바꾸는 조건부 UPDATE다. */
@Repository
class ReportTargetPersistenceAdapter : ReportTargetRepository {

    override fun exists(targetType: ReportTargetType, targetId: Long): Boolean {
        val (table, id, deletedAt) = target(targetType)
        return table.select(id).where { (id eq targetId) and deletedAt.isNull() }.limit(1).any()
    }

    override fun hide(targetType: ReportTargetType, targetId: Long): Boolean = when (targetType) {
        ReportTargetType.POST -> PostTable.update({ visible(PostTable.id, PostTable.deletedAt, PostTable.hidden, targetId) }) {
            it[hidden] = true
        } > 0

        ReportTargetType.COMMENT -> CommentTable.updateReturning(
            returning = listOf(CommentTable.postId),
            where = { visible(CommentTable.id, CommentTable.deletedAt, CommentTable.hidden, targetId) },
        ) {
            it[hidden] = true
            it[updatedAt] = CurrentTimestampWithTimeZone
        }.singleOrNull()?.get(CommentTable.postId)
            ?.also { postId -> decrease(PostTable, PostTable.id, PostTable.deletedAt, PostTable.commentCount, postId) } != null

        ReportTargetType.SPOT_QUESTION -> SpotQuestionTable.update({
            visible(SpotQuestionTable.id, SpotQuestionTable.deletedAt, SpotQuestionTable.hidden, targetId)
        }) { it[hidden] = true } > 0

        ReportTargetType.SPOT_ANSWER -> SpotAnswerTable.updateReturning(
            returning = listOf(SpotAnswerTable.questionId),
            where = { visible(SpotAnswerTable.id, SpotAnswerTable.deletedAt, SpotAnswerTable.hidden, targetId) },
        ) { it[hidden] = true }.singleOrNull()?.get(SpotAnswerTable.questionId)
            ?.also { questionId ->
                decrease(SpotQuestionTable, SpotQuestionTable.id, SpotQuestionTable.deletedAt, SpotQuestionTable.answerCount, questionId)
            } != null

        // 답글은 어떤 계수에도 잡히지 않아 숨기기만 한다.
        ReportTargetType.SPOT_ANSWER_REPLY -> SpotAnswerReplyTable.update({
            visible(SpotAnswerReplyTable.id, SpotAnswerReplyTable.deletedAt, SpotAnswerReplyTable.hidden, targetId)
        }) { it[hidden] = true } > 0

        ReportTargetType.MEMBER -> false
    }

    private fun visible(id: Column<Long>, deletedAt: Column<OffsetDateTime?>, hidden: Column<Boolean>, targetId: Long) = (id eq targetId) and deletedAt.isNull() and (hidden eq false)

    private fun decrease(table: Table, id: Column<Long>, deletedAt: Column<OffsetDateTime?>, counter: Column<Int>, rowId: Long) {
        table.update({ (id eq rowId) and deletedAt.isNull() and (counter greater 0) }) { it[counter] = counter - 1 }
    }

    /** 회원 신고는 auth가 존재를 판정하므로 여기에 오지 않는다. */
    private fun target(targetType: ReportTargetType): Triple<Table, Column<Long>, Column<OffsetDateTime?>> = when (targetType) {
        ReportTargetType.POST -> Triple(PostTable, PostTable.id, PostTable.deletedAt)
        ReportTargetType.COMMENT -> Triple(CommentTable, CommentTable.id, CommentTable.deletedAt)
        ReportTargetType.SPOT_QUESTION -> Triple(SpotQuestionTable, SpotQuestionTable.id, SpotQuestionTable.deletedAt)
        ReportTargetType.SPOT_ANSWER -> Triple(SpotAnswerTable, SpotAnswerTable.id, SpotAnswerTable.deletedAt)
        ReportTargetType.SPOT_ANSWER_REPLY -> Triple(SpotAnswerReplyTable, SpotAnswerReplyTable.id, SpotAnswerReplyTable.deletedAt)
        ReportTargetType.MEMBER -> error("회원 신고 대상은 auth가 확인한다.")
    }
}
