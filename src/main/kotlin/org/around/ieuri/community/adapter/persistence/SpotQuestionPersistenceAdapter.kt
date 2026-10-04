package org.around.ieuri.community.adapter.persistence

import org.around.ieuri.community.adapter.persistence.mapper.toSpotQuestion
import org.around.ieuri.community.adapter.persistence.table.SpotAnswerReplyTable
import org.around.ieuri.community.adapter.persistence.table.SpotAnswerTable
import org.around.ieuri.community.adapter.persistence.table.SpotQuestionTable
import org.around.ieuri.community.application.question.required.SpotQuestionRepository
import org.around.ieuri.community.domain.question.SpotQuestion
import org.around.ieuri.community.domain.shared.AttachedPhotos
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.minus
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository

@Repository
class SpotQuestionPersistenceAdapter : SpotQuestionRepository {

    override fun findById(questionId: Long): SpotQuestion? = SpotQuestionTable.selectAll()
        .where { (SpotQuestionTable.id eq questionId) and SpotQuestionTable.deletedAt.isNull() }
        .singleOrNull()?.toSpotQuestion()

    override fun exists(questionId: Long): Boolean = SpotQuestionTable.select(SpotQuestionTable.id)
        .where { (SpotQuestionTable.id eq questionId) and SpotQuestionTable.deletedAt.isNull() }.limit(1).any()

    override fun insert(question: SpotQuestion, photos: AttachedPhotos): Long {
        val questionId = SpotQuestionTable.insertReturning(listOf(SpotQuestionTable.id)) {
            it[catalogId] = question.catalogId
            it[authorId] = question.authorId
            it[body] = question.body
            it[resolved] = question.resolved
        }.single()[SpotQuestionTable.id]
        insertSpotQaPhotos(SpotQaPhotoOwner.QUESTION, questionId, photos)
        return questionId
    }

    override fun changeResolved(questionId: Long, resolved: Boolean): Boolean = SpotQuestionTable.update({
        (SpotQuestionTable.id eq questionId) and SpotQuestionTable.deletedAt.isNull()
    }) {
        it[SpotQuestionTable.resolved] = resolved
        it[updatedAt] = CurrentTimestampWithTimeZone
    } > 0

    /** 순서 주의: 답글 → 답변 → 질문 사진을 먼저 지운 뒤 질문을 지운다. 답변·답글 행은 FK CASCADE가 따라 지운다. */
    override fun deleteWithDescendants(questionId: Long) {
        val answerIds = SpotAnswerTable.select(SpotAnswerTable.id).where { SpotAnswerTable.questionId eq questionId }
        val replyIds = SpotAnswerReplyTable.select(SpotAnswerReplyTable.id).where { SpotAnswerReplyTable.answerId inSubQuery answerIds }
        deleteSpotQaPhotos(SpotQaPhotoOwner.REPLY, replyIds)
        deleteSpotQaPhotos(SpotQaPhotoOwner.ANSWER, answerIds)
        deleteSpotQaPhotos(SpotQaPhotoOwner.QUESTION, questionId)
        SpotQuestionTable.deleteWhere { SpotQuestionTable.id eq questionId }
    }

    override fun hideIfVisible(questionId: Long): Boolean = SpotQuestionTable.update({
        (SpotQuestionTable.id eq questionId) and SpotQuestionTable.deletedAt.isNull() and (SpotQuestionTable.hidden eq false)
    }) { it[hidden] = true } > 0

    override fun increaseAnswerCount(questionId: Long) {
        SpotQuestionTable.update({ (SpotQuestionTable.id eq questionId) and SpotQuestionTable.deletedAt.isNull() }) {
            it[answerCount] = answerCount + 1
        }
    }

    override fun decreaseAnswerCount(questionId: Long) {
        SpotQuestionTable.update({
            (SpotQuestionTable.id eq questionId) and SpotQuestionTable.deletedAt.isNull() and (SpotQuestionTable.answerCount greater 0)
        }) { it[answerCount] = answerCount - 1 }
    }
}
