package org.around.ieuri.community.adapter.persistence

import org.around.ieuri.common.security.AesGcmCipher
import org.around.ieuri.community.adapter.persistence.mapper.toConversation
import org.around.ieuri.community.adapter.persistence.mapper.toMessage
import org.around.ieuri.community.adapter.persistence.mapper.toSpotQaOrigin
import org.around.ieuri.community.adapter.persistence.mapper.toTimestamptz
import org.around.ieuri.community.adapter.persistence.mapper.writeQuoteTo
import org.around.ieuri.community.adapter.persistence.mapper.writeTo
import org.around.ieuri.community.adapter.persistence.table.ConversationTable
import org.around.ieuri.community.adapter.persistence.table.MessageQuoteTable
import org.around.ieuri.community.adapter.persistence.table.MessageTable
import org.around.ieuri.community.application.conversation.provided.dto.ExpiredMessages
import org.around.ieuri.community.application.conversation.required.ConversationRepository
import org.around.ieuri.community.domain.conversation.Conversation
import org.around.ieuri.community.domain.conversation.Message
import org.around.ieuri.community.domain.conversation.SpotQaOrigin
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository
import java.time.Instant

/** 대화·메시지. 본문과 인용 발췌는 여기서 암복호화한다(POL-050). */
@Repository
class ConversationPersistenceAdapter(
    private val cipher: AesGcmCipher,
) : ConversationRepository {

    override fun findById(conversationId: Long): Conversation? = ConversationTable.selectAll()
        .where { ConversationTable.id eq conversationId }
        .singleOrNull()?.toConversation()

    /** 경합으로 같은 쌍이 먼저 들어갔으면 삽입은 아무것도 하지 않고, 그 행의 id를 읽는다. */
    override fun insertIfAbsent(conversation: Conversation): Long {
        ConversationTable.insertIgnore { conversation.writeTo(it) }
        return ConversationTable.select(ConversationTable.id)
            .where { (ConversationTable.memberLow eq conversation.memberLow) and (ConversationTable.memberHigh eq conversation.memberHigh) }
            .single()[ConversationTable.id]
    }

    override fun insertMessage(message: Message): Message {
        val row = MessageTable.insertReturning(listOf(MessageTable.id, MessageTable.createdAt)) { message.writeTo(it, cipher::encrypt) }
            .single()
        val messageId = row[MessageTable.id]
        message.spotQaOrigin?.let { origin -> MessageQuoteTable.insert { origin.writeQuoteTo(it, messageId, cipher::encrypt) } }
        return Message(
            id = messageId,
            conversationId = message.conversationId,
            senderId = message.senderId,
            body = message.body,
            photoKey = message.photoKey,
            spotQaOrigin = message.spotQaOrigin,
            createdAt = row[MessageTable.createdAt].toInstant(),
        )
    }

    override fun findMessages(messageIds: List<Long>): List<Message> {
        if (messageIds.isEmpty()) return emptyList()
        val origins = MessageQuoteTable.selectAll().where { MessageQuoteTable.messageId inList messageIds }
            .associate { quote ->
                quote[MessageQuoteTable.messageId] to quote.toSpotQaOrigin(
                    decryptedQuestionExcerpt = cipher.decrypt(quote[MessageQuoteTable.primaryExcerptEncrypted]),
                    decryptedAnswerExcerpt = quote[MessageQuoteTable.secondaryExcerptEncrypted]?.let(cipher::decrypt).orEmpty(),
                )
            }
        return MessageTable.selectAll()
            .where { (MessageTable.id inList messageIds) and MessageTable.deletedAt.isNull() }
            // 본문이 빈 과거 메시지는 복호화를 건너뛴다 — 빈 값은 base64 디코딩에서 터진다.
            .map { it.toMessage(it[MessageTable.bodyEncrypted].takeIf(String::isNotEmpty)?.let(cipher::decrypt).orEmpty(), origins[it[MessageTable.id]]) }
    }

    /** 읽음 지점을 지워진 메시지로 밀지 않는다 — 세는 쪽과 같은 집합을 본다. */
    override fun lastMessageId(conversationId: Long): Long? {
        val maxId = MessageTable.id.max()
        return MessageTable.select(maxId)
            .where { (MessageTable.conversationId eq conversationId) and MessageTable.deletedAt.isNull() }
            .single()[maxId]
    }

    override fun advanceLastMessageAt(conversationId: Long, at: Instant) {
        val value = at.toTimestamptz()
        ConversationTable.update({ (ConversationTable.id eq conversationId) and (ConversationTable.lastMessageAt less value) }) {
            it[lastMessageAt] = value
        }
    }

    override fun advanceReadPoints(conversation: Conversation) {
        val id = requireNotNull(conversation.id) { "적재된 대화만 읽음 지점을 쓴다." }
        conversation.lastReadMessageIdLow?.let { advance(id, ConversationTable.lastReadMessageIdLow, it) }
        conversation.lastReadMessageIdHigh?.let { advance(id, ConversationTable.lastReadMessageIdHigh, it) }
    }

    /** 목록과 같은 집합(지워지지 않은 메시지)을 센다 — 다르면 뱃지가 사라지지 않는다. */
    override fun countUnread(conversationId: Long, memberId: Long, afterMessageId: Long?): Int = MessageTable.select(MessageTable.id)
        .where {
            val base = (MessageTable.conversationId eq conversationId) and (MessageTable.senderId neq memberId) and MessageTable.deletedAt.isNull()
            afterMessageId?.let { base and (MessageTable.id greater it) } ?: base
        }
        .count().toInt()

    /** 키는 행 삭제 전에 모은다. 인용·신고 항목 행은 FK CASCADE가 따라 지운다. */
    override fun deleteMessagesCreatedBefore(cutoff: Instant, limit: Int): ExpiredMessages {
        val targets = MessageTable.select(MessageTable.id, MessageTable.photoObjectKey)
            .where { MessageTable.createdAt less cutoff.toTimestamptz() }
            .limit(limit)
            .toList()
        if (targets.isEmpty()) return ExpiredMessages(deletedCount = 0, photoObjectKeys = emptyList())

        val deleted = MessageTable.deleteWhere { MessageTable.id inList targets.map { it[MessageTable.id] } }
        return ExpiredMessages(deletedCount = deleted, photoObjectKeys = targets.mapNotNull { it[MessageTable.photoObjectKey] })
    }

    private fun advance(conversationId: Long, column: Column<Long?>, messageId: Long) {
        ConversationTable.update({ (ConversationTable.id eq conversationId) and (column.isNull() or (column less messageId)) }) {
            it[column] = messageId
        }
    }

    override fun findIdsByParticipant(memberId: Long): List<Long> = ConversationTable.select(ConversationTable.id)
        .where { (ConversationTable.memberLow eq memberId) or (ConversationTable.memberHigh eq memberId) }
        .map { it[ConversationTable.id] }

    override fun markMessagesDeletedBySender(conversationId: Long, senderId: Long, deletedAt: Instant): Int = MessageTable.update({
        (MessageTable.conversationId eq conversationId) and (MessageTable.senderId eq senderId) and MessageTable.deletedAt.isNull()
    }) { it[MessageTable.deletedAt] = deletedAt.toTimestamptz() }
}
