package org.around.ieuri.community.adapter.persistence.query

import org.around.ieuri.common.pagination.CursorEdge
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.security.AesGcmCipher
import org.around.ieuri.community.adapter.persistence.mapper.toMessageView
import org.around.ieuri.community.adapter.persistence.mapper.toSpotQaOriginView
import org.around.ieuri.community.adapter.persistence.table.ConversationTable
import org.around.ieuri.community.adapter.persistence.table.MessageQuoteTable
import org.around.ieuri.community.adapter.persistence.table.MessageTable
import org.around.ieuri.community.application.conversation.provided.dto.ConversationSpotQaOriginView
import org.around.ieuri.community.application.conversation.provided.dto.ConversationView
import org.around.ieuri.community.application.conversation.provided.dto.MessageView
import org.around.ieuri.community.application.conversation.required.ConversationMessagesSearch
import org.around.ieuri.community.application.conversation.required.ConversationQueryPort
import org.around.ieuri.community.application.conversation.required.ConversationsSearch
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.eqSubQuery
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Component

/**
 * 대화·메시지 읽기 어댑터. 본문을 여기서 복호화해 read DTO에 평문을 담는다. 트랜잭션은 조회 서비스가 연다.
 * - 메시지 조회는 참여자 판정을 하지 않는다 — 서비스가 판정해 FORBIDDEN을 고른다.
 */
@Component
class ConversationQueryAdapter(
    private val cipher: AesGcmCipher,
) : ConversationQueryPort {

    /** 내가 참여한 대화. 최근 활동순. 마지막 메시지와 안 읽은 수는 페이지 단위로 한 번에 읽는다(N+1 방지). */
    override fun findConversations(search: ConversationsSearch): CursorPage<ConversationView> {
        val cursor = search.after?.let(::decodeCursor)
        val rows = ConversationTable.selectAll()
            .where {
                allOf(
                    participant(search.memberId),
                    cursor?.let { keysetBefore(ConversationTable.lastMessageAt, ConversationTable.id, it) },
                )
            }
            .orderBy(ConversationTable.lastMessageAt to SortOrder.DESC, ConversationTable.id to SortOrder.DESC)
            .limit(search.first + 1)
            .toList()
        val pageIds = rows.take(search.first).map { it[ConversationTable.id] }
        val lastMessages = findLastMessagesOf(pageIds, search.memberId)
        val unreadCounts = countUnreadOf(pageIds, search.memberId)
        return page(rows, search.first, cursor != null) { row ->
            val id = row[ConversationTable.id]
            CursorEdge(
                encodeCursor(row[ConversationTable.lastMessageAt].toInstant(), id),
                row.toConversationView(search.memberId, lastMessages[id], unreadCounts[id] ?: 0),
            )
        }
    }

    /** 대화의 메시지. 최신순이다. */
    override fun findMessages(search: ConversationMessagesSearch): CursorPage<MessageView> {
        val cursor = search.after?.let(::decodeCursor)
        val rows = MessageTable.selectAll()
            .where {
                allOf(
                    MessageTable.conversationId eq search.conversationId,
                    MessageTable.deletedAt.isNull(),
                    cursor?.let { keysetBefore(MessageTable.createdAt, MessageTable.id, it) },
                )
            }
            .orderBy(MessageTable.createdAt to SortOrder.DESC, MessageTable.id to SortOrder.DESC)
            .limit(search.first + 1)
            .toList()
        val quotes = findQuotesOf(rows.take(search.first).map { it[MessageTable.id] })
        return page(rows, search.first, cursor != null) { row ->
            val id = row[MessageTable.id]
            CursorEdge(encodeCursor(row[MessageTable.createdAt].toInstant(), id), row.toView(search.memberId, quotes[id]))
        }
    }

    override fun findConversation(conversationId: Long, memberId: Long): ConversationView? {
        val row = ConversationTable.selectAll()
            .where { (ConversationTable.id eq conversationId) and participant(memberId) }
            .singleOrNull() ?: return null
        return row.toConversationView(
            memberId,
            findLastMessagesOf(listOf(conversationId), memberId)[conversationId],
            countUnreadOf(listOf(conversationId), memberId)[conversationId] ?: 0,
        )
    }

    /** 대화로도 좁힌다(채널 이름을 인가 근거로 쓰지 않음). 지워진 메시지면 null이다. */
    override fun findMessage(conversationId: Long, messageId: Long, memberId: Long): MessageView? = MessageTable.selectAll()
        .where { (MessageTable.id eq messageId) and (MessageTable.conversationId eq conversationId) and MessageTable.deletedAt.isNull() }
        .singleOrNull()
        ?.let { it.toView(memberId, findQuotesOf(listOf(messageId))[messageId]) }

    /**
     * 내 읽음 지점 이후, 상대가 보낸, 지워지지 않은 메시지 수. 읽음 지점이 null이면 전부다. 0인 대화는 키가 없다.
     * - 목록과 같은 집합(지워지지 않은 메시지)을 센다 — 다르면 뱃지가 사라지지 않는다.
     */
    private fun countUnreadOf(conversationIds: List<Long>, memberId: Long): Map<Long, Int> {
        if (conversationIds.isEmpty()) return emptyMap()
        val count = MessageTable.id.count()
        return MessageTable
            .join(ConversationTable, JoinType.INNER, MessageTable.conversationId, ConversationTable.id)
            .select(MessageTable.conversationId, count)
            .where {
                (MessageTable.conversationId inList conversationIds) and
                    (MessageTable.senderId neq memberId) and
                    MessageTable.deletedAt.isNull() and
                    afterMyReadPoint(memberId)
            }
            .groupBy(MessageTable.conversationId)
            .associate { it[MessageTable.conversationId] to it[count].toInt() }
    }

    /** 대화별 최대 createdAt 행만 읽고(상관 서브쿼리), 동시각이면 id가 큰 쪽을 취한다. */
    private fun findLastMessagesOf(conversationIds: List<Long>, memberId: Long): Map<Long, MessageView> {
        if (conversationIds.isEmpty()) return emptyMap()
        val latest = MessageTable.alias("latest_message")
        // 서브쿼리에도 deletedAt 조건이 필요하다 — 빼면 미리보기가 빈 칸이 된다.
        val latestCreatedAt = latest.select(latest[MessageTable.createdAt].max())
            .where { (latest[MessageTable.conversationId] eq MessageTable.conversationId) and latest[MessageTable.deletedAt].isNull() }
        return MessageTable.selectAll()
            .where {
                (MessageTable.conversationId inList conversationIds) and
                    MessageTable.deletedAt.isNull() and
                    (MessageTable.createdAt eqSubQuery latestCreatedAt)
            }
            .groupBy { it[MessageTable.conversationId] }
            .mapValues { (_, rows) -> rows.maxBy { it[MessageTable.id] }.toView(memberId, null) }
    }

    /** 인용을 한 번에 읽고 발췌를 복호화한다. 인용이 없는 메시지는 키가 없다. */
    private fun findQuotesOf(messageIds: List<Long>): Map<Long, ConversationSpotQaOriginView> {
        if (messageIds.isEmpty()) return emptyMap()
        return MessageQuoteTable.selectAll()
            .where { MessageQuoteTable.messageId inList messageIds }
            .associate { quote ->
                quote[MessageQuoteTable.messageId] to quote.toSpotQaOriginView(
                    decryptedQuestionExcerpt = cipher.decrypt(quote[MessageQuoteTable.primaryExcerptEncrypted]),
                    decryptedAnswerExcerpt = quote[MessageQuoteTable.secondaryExcerptEncrypted]?.let(cipher::decrypt).orEmpty(),
                )
            }
    }

    private fun participant(memberId: Long): Op<Boolean> = (ConversationTable.memberLow eq memberId) or (ConversationTable.memberHigh eq memberId)

    /** 내 쪽 읽음 지점(낮은 쪽이면 low, 아니면 high) 뒤의 메시지. */
    private fun afterMyReadPoint(memberId: Long): Op<Boolean> {
        fun after(readPoint: Column<Long?>) = readPoint.isNull() or (MessageTable.id greater readPoint)
        return ((ConversationTable.memberLow eq memberId) and after(ConversationTable.lastReadMessageIdLow)) or
            ((ConversationTable.memberLow neq memberId) and after(ConversationTable.lastReadMessageIdHigh))
    }

    private fun ResultRow.toConversationView(memberId: Long, lastMessage: MessageView?, unreadCount: Int) = ConversationView(
        id = this[ConversationTable.id],
        counterpartId = if (this[ConversationTable.memberLow] == memberId) this[ConversationTable.memberHigh] else this[ConversationTable.memberLow],
        lastMessage = lastMessage,
        unreadCount = unreadCount,
        updatedAt = this[ConversationTable.lastMessageAt].toInstant(),
    )

    /** 본문이 빈 과거 메시지는 복호화를 건너뛴다 — 빈 값은 base64 디코딩에서 터진다. */
    private fun ResultRow.toView(memberId: Long, origin: ConversationSpotQaOriginView?): MessageView {
        val encrypted = this[MessageTable.bodyEncrypted]
        return toMessageView(memberId, if (encrypted.isEmpty()) "" else cipher.decrypt(encrypted), origin)
    }
}
