package org.around.ieuri.community.adapter.persistence

import org.around.ieuri.community.adapter.persistence.mapper.toSpotAnswer
import org.around.ieuri.community.adapter.persistence.table.SpotAnswerReplyTable
import org.around.ieuri.community.adapter.persistence.table.SpotAnswerTable
import org.around.ieuri.community.application.answer.required.SpotAnswerRepository
import org.around.ieuri.community.domain.answer.SpotAnswer
import org.around.ieuri.community.domain.shared.AttachedPhotos
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.deleteReturning
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.updateReturning
import org.springframework.stereotype.Repository

@Repository
class SpotAnswerPersistenceAdapter : SpotAnswerRepository {

    override fun findById(answerId: Long): SpotAnswer? = SpotAnswerTable.selectAll()
        .where { (SpotAnswerTable.id eq answerId) and SpotAnswerTable.deletedAt.isNull() }
        .singleOrNull()?.toSpotAnswer()

    override fun exists(answerId: Long): Boolean = SpotAnswerTable.select(SpotAnswerTable.id)
        .where { (SpotAnswerTable.id eq answerId) and SpotAnswerTable.deletedAt.isNull() }.limit(1).any()

    override fun insert(answer: SpotAnswer, photos: AttachedPhotos): Long {
        val answerId = SpotAnswerTable.insertReturning(listOf(SpotAnswerTable.id)) {
            it[questionId] = answer.questionId
            it[catalogId] = answer.catalogId
            it[authorId] = answer.authorId
            it[body] = answer.body
        }.single()[SpotAnswerTable.id]
        insertSpotQaPhotos(SpotQaPhotoOwner.ANSWER, answerId, photos)
        return answerId
    }

    /** 순서 주의: 답글 → 답변 사진을 먼저 지운 뒤 답변을 지운다. 답글 행은 FK CASCADE가 따라 지운다. */
    override fun deleteWithRepliesWasCounted(answerId: Long): Boolean {
        deleteSpotQaPhotos(SpotQaPhotoOwner.REPLY, SpotAnswerReplyTable.select(SpotAnswerReplyTable.id).where { SpotAnswerReplyTable.answerId eq answerId })
        deleteSpotQaPhotos(SpotQaPhotoOwner.ANSWER, answerId)
        return SpotAnswerTable.deleteReturning(listOf(SpotAnswerTable.hidden)) { SpotAnswerTable.id eq answerId }
            .singleOrNull()?.let { !it[SpotAnswerTable.hidden] } ?: false
    }

    override fun hideIfVisible(answerId: Long): Long? = SpotAnswerTable.updateReturning(
        returning = listOf(SpotAnswerTable.questionId),
        where = { (SpotAnswerTable.id eq answerId) and SpotAnswerTable.deletedAt.isNull() and (SpotAnswerTable.hidden eq false) },
    ) { it[hidden] = true }.singleOrNull()?.get(SpotAnswerTable.questionId)
}
