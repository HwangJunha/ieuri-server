package org.around.ieuri.community.adapter.persistence

import org.around.ieuri.community.adapter.persistence.mapper.toSpotAnswer
import org.around.ieuri.community.adapter.persistence.mapper.toSpotAnswerReply
import org.around.ieuri.community.adapter.persistence.mapper.toSpotQuestion
import org.around.ieuri.community.adapter.persistence.mapper.toTimestamptz
import org.around.ieuri.community.adapter.persistence.table.SpotAnswerReplyTable
import org.around.ieuri.community.adapter.persistence.table.SpotAnswerTable
import org.around.ieuri.community.adapter.persistence.table.SpotQaPhotoTable
import org.around.ieuri.community.adapter.persistence.table.SpotQuestionTable
import org.around.ieuri.community.application.question.required.SpotQuestionRepository
import org.around.ieuri.community.domain.question.SpotAnswer
import org.around.ieuri.community.domain.question.SpotAnswerReply
import org.around.ieuri.community.domain.question.SpotQaKind
import org.around.ieuri.community.domain.question.SpotQuestion
import org.around.ieuri.community.domain.shared.AttachedPhotos
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.minus
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteReturning
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.updateReturning
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class SpotQuestionPersistenceAdapter : SpotQuestionRepository {

    override fun findById(questionId: Long): SpotQuestion? = SpotQuestionTable.selectAll()
        .where { (SpotQuestionTable.id eq questionId) and SpotQuestionTable.deletedAt.isNull() }
        .singleOrNull()?.toSpotQuestion()

    override fun findByAnswerId(answerId: Long): SpotQuestion? = SpotQuestionTable
        .join(SpotAnswerTable, JoinType.INNER, SpotQuestionTable.id, SpotAnswerTable.questionId)
        .select(SpotQuestionTable.columns)
        .where {
            (SpotAnswerTable.id eq answerId) and SpotAnswerTable.deletedAt.isNull() and SpotQuestionTable.deletedAt.isNull()
        }
        .singleOrNull()?.toSpotQuestion()

    override fun findAnswer(answerId: Long): SpotAnswer? = SpotAnswerTable.selectAll()
        .where { (SpotAnswerTable.id eq answerId) and SpotAnswerTable.deletedAt.isNull() }
        .singleOrNull()?.toSpotAnswer()

    override fun findReply(replyId: Long): SpotAnswerReply? = SpotAnswerReplyTable.selectAll()
        .where { (SpotAnswerReplyTable.id eq replyId) and SpotAnswerReplyTable.deletedAt.isNull() }
        .singleOrNull()?.toSpotAnswerReply()

    override fun exists(questionId: Long): Boolean = SpotQuestionTable.select(SpotQuestionTable.id)
        .where { (SpotQuestionTable.id eq questionId) and SpotQuestionTable.deletedAt.isNull() }.limit(1).any()

    override fun answerExists(answerId: Long): Boolean = SpotAnswerTable.select(SpotAnswerTable.id)
        .where { (SpotAnswerTable.id eq answerId) and SpotAnswerTable.deletedAt.isNull() }.limit(1).any()

    override fun replyExists(replyId: Long): Boolean = SpotAnswerReplyTable.select(SpotAnswerReplyTable.id)
        .where { (SpotAnswerReplyTable.id eq replyId) and SpotAnswerReplyTable.deletedAt.isNull() }.limit(1).any()

    override fun insert(question: SpotQuestion, photos: AttachedPhotos): Long {
        val questionId = SpotQuestionTable.insertReturning(listOf(SpotQuestionTable.id)) {
            it[catalogId] = question.catalogId
            it[authorId] = question.authorId
            it[body] = question.body
            it[resolved] = question.resolved
        }.single()[SpotQuestionTable.id]
        insertPhotos(question.kind, questionId, photos)
        return questionId
    }

    override fun insertAnswer(answer: SpotAnswer, photos: AttachedPhotos): Long {
        val answerId = SpotAnswerTable.insertReturning(listOf(SpotAnswerTable.id)) {
            it[questionId] = answer.questionId
            it[catalogId] = answer.catalogId
            it[authorId] = answer.authorId
            it[body] = answer.body
        }.single()[SpotAnswerTable.id]
        insertPhotos(answer.kind, answerId, photos)
        return answerId
    }

    override fun insertReply(reply: SpotAnswerReply, photos: AttachedPhotos): Long {
        val replyId = SpotAnswerReplyTable.insertReturning(listOf(SpotAnswerReplyTable.id)) {
            it[answerId] = reply.answerId
            it[catalogId] = reply.catalogId
            it[authorId] = reply.authorId
            it[body] = reply.body
        }.single()[SpotAnswerReplyTable.id]
        insertPhotos(reply.kind, replyId, photos)
        return replyId
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
        deletePhotos(SpotQaKind.REPLY, replyIds)
        deletePhotos(SpotQaKind.ANSWER, answerIds)
        deletePhotos(SpotQaKind.QUESTION, questionId)
        SpotQuestionTable.deleteWhere { SpotQuestionTable.id eq questionId }
    }

    /** 순서 주의: 답글 → 답변 사진을 먼저 지운 뒤 답변을 지운다. 답글 행은 FK CASCADE가 따라 지운다. */
    override fun deleteAnswerWasCounted(answerId: Long): Boolean {
        deletePhotos(SpotQaKind.REPLY, SpotAnswerReplyTable.select(SpotAnswerReplyTable.id).where { SpotAnswerReplyTable.answerId eq answerId })
        deletePhotos(SpotQaKind.ANSWER, answerId)
        return SpotAnswerTable.deleteReturning(listOf(SpotAnswerTable.hidden)) { SpotAnswerTable.id eq answerId }
            .singleOrNull()?.let { !it[SpotAnswerTable.hidden] } ?: false
    }

    /** 사진을 먼저 지운다(FK가 없다). */
    override fun deleteReply(replyId: Long) {
        deletePhotos(SpotQaKind.REPLY, replyId)
        SpotAnswerReplyTable.deleteWhere { SpotAnswerReplyTable.id eq replyId }
    }

    override fun hideIfVisible(questionId: Long): Boolean = SpotQuestionTable.update({
        (SpotQuestionTable.id eq questionId) and SpotQuestionTable.deletedAt.isNull() and (SpotQuestionTable.hidden eq false)
    }) { it[hidden] = true } > 0

    override fun hideAnswerIfVisible(answerId: Long): Long? = SpotAnswerTable.updateReturning(
        returning = listOf(SpotAnswerTable.questionId),
        where = { (SpotAnswerTable.id eq answerId) and SpotAnswerTable.deletedAt.isNull() and (SpotAnswerTable.hidden eq false) },
    ) { it[hidden] = true }.singleOrNull()?.get(SpotAnswerTable.questionId)

    override fun hideReplyIfVisible(replyId: Long): Boolean = SpotAnswerReplyTable.update({
        (SpotAnswerReplyTable.id eq replyId) and SpotAnswerReplyTable.deletedAt.isNull() and (SpotAnswerReplyTable.hidden eq false)
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

    override fun markDeletedByAuthor(authorId: Long, deletedAt: Instant): Int = SpotQuestionTable.update({
        (SpotQuestionTable.authorId eq authorId) and SpotQuestionTable.deletedAt.isNull()
    }) { it[SpotQuestionTable.deletedAt] = deletedAt.toTimestamptz() }

    override fun markAnswersDeletedByAuthorCounted(authorId: Long, deletedAt: Instant): List<Long> = SpotAnswerTable.updateReturning(
        returning = listOf(SpotAnswerTable.questionId, SpotAnswerTable.hidden),
        where = { (SpotAnswerTable.authorId eq authorId) and SpotAnswerTable.deletedAt.isNull() },
    ) { it[SpotAnswerTable.deletedAt] = deletedAt.toTimestamptz() }
        .filterNot { it[SpotAnswerTable.hidden] }.map { it[SpotAnswerTable.questionId] }

    override fun markRepliesDeletedByAuthor(authorId: Long, deletedAt: Instant): Int = SpotAnswerReplyTable.update({
        (SpotAnswerReplyTable.authorId eq authorId) and SpotAnswerReplyTable.deletedAt.isNull()
    }) { it[SpotAnswerReplyTable.deletedAt] = deletedAt.toTimestamptz() }

    // 사진은 다형 참조(owner_type·owner_id)라 FK가 없다 — 주인을 지우기 전에 사진을 먼저 지운다.
    private fun insertPhotos(kind: SpotQaKind, ownerId: Long, photos: AttachedPhotos) {
        SpotQaPhotoTable.batchInsert(photos.keys.withIndex(), shouldReturnGeneratedValues = false) { (index, key) ->
            this[SpotQaPhotoTable.ownerType] = kind.name
            this[SpotQaPhotoTable.ownerId] = ownerId
            this[SpotQaPhotoTable.objectKey] = key.value
            this[SpotQaPhotoTable.position] = index.toShort()
        }
    }

    private fun deletePhotos(kind: SpotQaKind, ownerId: Long) {
        SpotQaPhotoTable.deleteWhere { (SpotQaPhotoTable.ownerType eq kind.name) and (SpotQaPhotoTable.ownerId eq ownerId) }
    }

    /** [ownerIds]는 주인 id 한 열을 고르는 서브쿼리다. */
    private fun deletePhotos(kind: SpotQaKind, ownerIds: Query) {
        SpotQaPhotoTable.deleteWhere { (SpotQaPhotoTable.ownerType eq kind.name) and (SpotQaPhotoTable.ownerId inSubQuery ownerIds) }
    }
}
