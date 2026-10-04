package org.around.ieuri.community.adapter.persistence

import org.around.ieuri.community.adapter.persistence.mapper.toSpotAnswerReply
import org.around.ieuri.community.adapter.persistence.table.SpotAnswerReplyTable
import org.around.ieuri.community.application.reply.required.SpotAnswerReplyRepository
import org.around.ieuri.community.domain.reply.SpotAnswerReply
import org.around.ieuri.community.domain.shared.AttachedPhotos
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository

@Repository
class SpotAnswerReplyPersistenceAdapter : SpotAnswerReplyRepository {

    override fun findById(replyId: Long): SpotAnswerReply? = SpotAnswerReplyTable.selectAll()
        .where { (SpotAnswerReplyTable.id eq replyId) and SpotAnswerReplyTable.deletedAt.isNull() }
        .singleOrNull()?.toSpotAnswerReply()

    override fun exists(replyId: Long): Boolean = SpotAnswerReplyTable.select(SpotAnswerReplyTable.id)
        .where { (SpotAnswerReplyTable.id eq replyId) and SpotAnswerReplyTable.deletedAt.isNull() }.limit(1).any()

    override fun insert(reply: SpotAnswerReply, photos: AttachedPhotos): Long {
        val replyId = SpotAnswerReplyTable.insertReturning(listOf(SpotAnswerReplyTable.id)) {
            it[answerId] = reply.answerId
            it[catalogId] = reply.catalogId
            it[authorId] = reply.authorId
            it[body] = reply.body
        }.single()[SpotAnswerReplyTable.id]
        insertSpotQaPhotos(SpotQaPhotoOwner.REPLY, replyId, photos)
        return replyId
    }

    /** 사진을 먼저 지운다(FK가 없다). */
    override fun delete(replyId: Long) {
        deleteSpotQaPhotos(SpotQaPhotoOwner.REPLY, replyId)
        SpotAnswerReplyTable.deleteWhere { SpotAnswerReplyTable.id eq replyId }
    }

    override fun hideIfVisible(replyId: Long): Boolean = SpotAnswerReplyTable.update({
        (SpotAnswerReplyTable.id eq replyId) and SpotAnswerReplyTable.deletedAt.isNull() and (SpotAnswerReplyTable.hidden eq false)
    }) { it[hidden] = true } > 0
}
