package org.around.ieuri.community.infrastructure.adapter.out.query

import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.core.types.dsl.DateTimePath
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.core.types.dsl.NumberPath
import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.common.pagination.CursorEdge
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.cursorPageOf
import org.around.ieuri.common.security.AesGcmCipher
import org.around.ieuri.community.application.port.out.query.ConversationMessagesSearch
import org.around.ieuri.community.application.port.out.query.ConversationQueryPort
import org.around.ieuri.community.application.port.out.query.ConversationsSearch
import org.around.ieuri.community.application.query.ConversationSpotQaOriginView
import org.around.ieuri.community.application.query.ConversationView
import org.around.ieuri.community.application.query.MessageView
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.ConversationEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.MessageEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QConversationEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QMessageEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QMessageQuoteEntity
import org.around.ieuri.community.infrastructure.adapter.out.query.mapper.toSpotQaOriginView
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 대화·메시지 읽기 어댑터(SPEC-0017). 본문을 여기서 복호화해 read DTO에 평문을 담는다(R16).
 * - 메시지 조회는 참여자 판정을 하지 않는다 — 서비스가 판정해 FORBIDDEN을 고른다.
 */
@Component
class ConversationQueryAdapter(
    private val queryFactory: JPAQueryFactory,
    private val cipher: AesGcmCipher,
) : ConversationQueryPort {

    private val conversation = QConversationEntity.conversationEntity
    private val message = QMessageEntity.messageEntity
    private val quote = QMessageQuoteEntity.messageQuoteEntity

    /** 내가 참여한 대화. 최근 활동순. */
    @Transactional(readOnly = true)
    override fun findConversations(search: ConversationsSearch): CursorPage<ConversationView> {
        val cursor = search.after?.let { decodeCursor(it) }

        val rows = queryFactory
            .selectFrom(conversation)
            .where(
                participantCondition(search.memberId),
                cursor?.let { keysetCondition(conversation.lastMessageAt, conversation.id, it) },
            )
            .orderBy(conversation.lastMessageAt.desc(), conversation.id.desc())
            .limit((search.first + 1).toLong())
            .fetch()

        val hasNextPage = rows.size > search.first
        val pageRows = if (hasNextPage) rows.take(search.first) else rows
        // 마지막 메시지와 안 읽은 수는 페이지 단위로 한 번에 읽는다(N+1 방지).
        val pageIds = pageRows.mapNotNull { it.id }
        val lastMessages = findLastMessagesOf(pageIds, search.memberId)
        val unreadCounts = countUnreadOf(pageIds, search.memberId)

        val edges = pageRows.map { entity ->
            val lastMessageAt = requireNotNull(entity.lastMessageAt)
            val id = requireNotNull(entity.id)
            CursorEdge(
                cursor = encodeCursor(lastMessageAt, id),
                node = entity.toConversationView(
                    search.memberId,
                    lastMessages[id],
                    unreadCounts[id] ?: 0,
                ),
            )
        }
        return cursorPageOf(edges, hasNextPage = hasNextPage, hasPreviousPage = cursor != null)
    }

    /** 대화의 메시지. 최신순이다. */
    @Transactional(readOnly = true)
    override fun findMessages(search: ConversationMessagesSearch): CursorPage<MessageView> {
        val cursor = search.after?.let { decodeCursor(it) }

        val rows = queryFactory
            .selectFrom(message)
            .where(
                message.conversationId.eq(search.conversationId),
                message.deletedAt.isNull,
                cursor?.let { keysetCondition(message.createdAt, message.id, it) },
            )
            .orderBy(message.createdAt.desc(), message.id.desc())
            .limit((search.first + 1).toLong())
            .fetch()

        val hasNextPage = rows.size > search.first
        val pageRows = if (hasNextPage) rows.take(search.first) else rows
        val quotes = findQuotesOf(pageRows.mapNotNull { it.id })
        val edges = pageRows.map { entity ->
            val createdAt = requireNotNull(entity.createdAt)
            val id = requireNotNull(entity.id)
            CursorEdge(
                cursor = encodeCursor(createdAt, id),
                node = entity.toMessageView(search.memberId, quotes[id]),
            )
        }
        return cursorPageOf(edges, hasNextPage = hasNextPage, hasPreviousPage = cursor != null)
    }

    @Transactional(readOnly = true)
    override fun findConversation(conversationId: Long, memberId: Long): ConversationView? {
        val entity = queryFactory
            .selectFrom(conversation)
            .where(conversation.id.eq(conversationId), participantCondition(memberId))
            .fetchOne() ?: return null

        val id = requireNotNull(entity.id)
        return entity.toConversationView(
            memberId,
            findLastMessagesOf(listOf(id), memberId)[id],
            countUnreadOf(listOf(id), memberId)[id] ?: 0,
        )
    }

    @Transactional(readOnly = true)
    override fun findMessage(conversationId: Long, messageId: Long, memberId: Long): MessageView? = queryFactory
        .selectFrom(message)
        // 대화로도 좁힌다(채널 이름을 인가 근거로 쓰지 않음). 지워진 메시지면 null이다.
        .where(
            message.id.eq(messageId),
            message.conversationId.eq(conversationId),
            message.deletedAt.isNull,
        )
        .fetchOne()
        ?.let { it.toMessageView(memberId, findQuotesOf(listOf(messageId))[messageId]) }

    /**
     * 대화별 안 읽은 메시지 수를 한 번에 센다.
     * - 내 읽음 지점 이후, 상대가 보낸, 지워지지 않은 메시지. 읽음 지점이 null이면 전부다.
     * - 결과가 0인 대화는 키가 없다.
     */
    @Transactional(readOnly = true)
    override fun countUnreadOf(conversationIds: List<Long>, memberId: Long): Map<Long, Int> {
        if (conversationIds.isEmpty()) return emptyMap()

        val myLastRead = Expressions.cases()
            .`when`(conversation.memberLow.eq(memberId)).then(conversation.lastReadMessageIdLow)
            .otherwise(conversation.lastReadMessageIdHigh)

        return queryFactory
            .select(message.conversationId, message.count())
            .from(message)
            .join(conversation).on(conversation.id.eq(message.conversationId))
            .where(
                message.conversationId.`in`(conversationIds),
                message.senderId.ne(memberId),
                // 목록과 같은 집합을 세야 한다 — 빼면 뱃지가 사라지지 않는다.
                message.deletedAt.isNull,
                myLastRead.isNull.or(message.id.gt(myLastRead)),
            )
            .groupBy(message.conversationId)
            .fetch()
            .associate { row ->
                requireNotNull(row.get(message.conversationId)) to
                    (row.get(message.count()) ?: 0L).toInt()
            }
    }

    @Transactional(readOnly = true)
    override fun findLastMessageId(conversationId: Long): Long? = queryFactory
        .select(message.id.max())
        .from(message)
        // 읽음 지점을 지워진 메시지로 밀지 않는다 — 세는 쪽과 같은 집합을 봐야 한다.
        .where(message.conversationId.eq(conversationId), message.deletedAt.isNull)
        .fetchOne()

    /**
     * 대화별 마지막 메시지를 한 번에 읽는다. 대화별 최대 createdAt 행만 읽고(상관 서브쿼리),
     * 동시각이면 id가 큰 쪽을 취한다.
     */
    private fun findLastMessagesOf(
        conversationIds: List<Long>,
        memberId: Long,
    ): Map<Long, MessageView> {
        if (conversationIds.isEmpty()) return emptyMap()

        val latest = QMessageEntity("latestMessage")
        return queryFactory
            .selectFrom(message)
            .where(
                message.conversationId.`in`(conversationIds),
                message.deletedAt.isNull,
                message.createdAt.eq(
                    JPAExpressions
                        .select(latest.createdAt.max())
                        .from(latest)
                        // 서브쿼리에도 deletedAt 조건이 필요하다 — 빼면 미리보기가 빈 칸이 된다.
                        .where(
                            latest.conversationId.eq(message.conversationId),
                            latest.deletedAt.isNull,
                        ),
                ),
            )
            .fetch()
            .groupBy { it.conversationId }
            .mapValues { (_, messages) ->
                messages.maxBy { requireNotNull(it.id) }.toMessageView(memberId)
            }
    }

    /** 메시지들의 인용을 한 번에 읽고 발췌를 복호화한다. 인용이 없는 메시지는 키가 없다. */
    private fun findQuotesOf(messageIds: List<Long>): Map<Long, ConversationSpotQaOriginView> {
        if (messageIds.isEmpty()) return emptyMap()

        return queryFactory
            .selectFrom(quote)
            .where(quote.messageId.`in`(messageIds))
            .fetch()
            .associate { entity ->
                entity.messageId to entity.toSpotQaOriginView(
                    decryptedPrimaryExcerpt = cipher.decrypt(entity.primaryExcerptEncrypted),
                    decryptedSecondaryExcerpt = entity.secondaryExcerptEncrypted
                        ?.let { cipher.decrypt(it) }
                        .orEmpty(),
                )
            }
    }

    private fun participantCondition(memberId: Long): BooleanExpression = conversation.memberLow.eq(memberId).or(conversation.memberHigh.eq(memberId))

    private fun keysetCondition(
        timePath: DateTimePath<Instant>,
        idPath: NumberPath<Long>,
        cursor: CommunityCursor,
    ): BooleanExpression = timePath.lt(cursor.createdAt)
        .or(timePath.eq(cursor.createdAt).and(idPath.lt(cursor.id)))

    private fun ConversationEntity.toConversationView(
        memberId: Long,
        lastMessage: MessageView?,
        unreadCount: Int,
    ) = ConversationView(
        id = requireNotNull(id) { "조회된 대화에는 id가 있어야 한다." },
        counterpartId = if (memberLow == memberId) memberHigh else memberLow,
        lastMessage = lastMessage,
        unreadCount = unreadCount,
        updatedAt = requireNotNull(lastMessageAt) { "조회된 대화에는 lastMessageAt이 있어야 한다." },
    )

    /** 본문이 빈 메시지(사진만 등)는 복호화를 건너뛴다 — 빈 값은 base64 디코딩에서 터진다. */
    private fun MessageEntity.toMessageView(
        memberId: Long,
        spotQaOrigin: ConversationSpotQaOriginView? = null,
    ) = MessageView(
        id = requireNotNull(id) { "조회된 메시지에는 id가 있어야 한다." },
        body = if (bodyEncrypted.isEmpty()) "" else cipher.decrypt(bodyEncrypted),
        senderId = senderId,
        ownedByViewer = senderId == memberId,
        viewerId = memberId,
        createdAt = requireNotNull(createdAt) { "조회된 메시지에는 createdAt이 있어야 한다." },
        spotQaOrigin = spotQaOrigin,
        photoKey = photoObjectKey,
    )
}
