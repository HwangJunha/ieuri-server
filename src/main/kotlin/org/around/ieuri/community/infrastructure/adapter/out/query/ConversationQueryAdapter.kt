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
import org.around.ieuri.community.application.query.ConversationView
import org.around.ieuri.community.application.query.MessageView
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.ConversationEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.MessageEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QConversationEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QMessageEntity
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 대화·메시지 읽기 어댑터(SPEC-0017 R8).
 *
 * **본문 복호화를 여기서 한다**(R16) — 키를 아는 것은 어댑터이고 read DTO에는 평문이 담긴다.
 * 암호화 컬럼이라 본문으로 검색·정렬할 수 없으며, 대화 검색은 비범위다.
 *
 * **참여자 판정을 하지 않는다** — 조회에서 참여자가 아니면 최상위 errors[]의 FORBIDDEN이라
 * 서비스가 판정하고 예외를 고른다. 여기서 조용히 빈 결과를 주면 그 구분이 사라진다.
 */
@Component
class ConversationQueryAdapter(
    private val queryFactory: JPAQueryFactory,
    private val cipher: AesGcmCipher,
) : ConversationQueryPort {

    private val conversation = QConversationEntity.conversationEntity
    private val message = QMessageEntity.messageEntity

    /** 내가 참여한 대화. 두 컬럼 어느 쪽에도 내가 올 수 있어 OR로 묶는다. */
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
        // 목록에 실을 마지막 메시지를 **한 번에** 읽는다 — 대화마다 물으면 20건에 조회가
        // 20번 붙는다(N+1).
        val pageIds = pageRows.mapNotNull { it.id }
        val lastMessages = findLastMessagesOf(pageIds, search.memberId)
        // 안 읽은 수도 **한 번에** 센다 — 대화마다 물으면 여기서 N+1이 된다.
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

    /**
     * 대화의 메시지. **최신순이다** — 채팅 화면은 위로 올릴수록 과거를 더 불러온다.
     */
    @Transactional(readOnly = true)
    override fun findMessages(search: ConversationMessagesSearch): CursorPage<MessageView> {
        val cursor = search.after?.let { decodeCursor(it) }

        val rows = queryFactory
            .selectFrom(message)
            .where(
                message.conversationId.eq(search.conversationId),
                cursor?.let { keysetCondition(message.createdAt, message.id, it) },
            )
            .orderBy(message.createdAt.desc(), message.id.desc())
            .limit((search.first + 1).toLong())
            .fetch()

        val hasNextPage = rows.size > search.first
        val pageRows = if (hasNextPage) rows.take(search.first) else rows
        val edges = pageRows.map { entity ->
            val createdAt = requireNotNull(entity.createdAt)
            val id = requireNotNull(entity.id)
            CursorEdge(
                cursor = encodeCursor(createdAt, id),
                node = entity.toMessageView(search.memberId),
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
    override fun findMessage(conversationId: Long, messageId: Long, memberId: Long): MessageView? =
        queryFactory
            .selectFrom(message)
            // **대화로도 좁힌다** — 채널 이름이 인가의 근거가 되지 않게 한다.
            .where(message.id.eq(messageId), message.conversationId.eq(conversationId))
            .fetchOne()
            ?.toMessageView(memberId)

    /**
     * 대화별 안 읽은 메시지 수를 **한 번에** 센다 — 대화마다 물으면 20건에 조회가 20번 붙는다.
     *
     * "내가 읽은 지점 이후 + 내가 보내지 않은" 메시지를 센다. 읽음 지점이 null이면
     * (아직 아무것도 안 읽음) 상대가 보낸 전부가 대상이다.
     *
     * 읽음 지점이 내 위치(low/high)에 따라 다른 컬럼이라 CASE로 고른다 — 참여자가 둘로
     * 고정된 구조의 대가이며, 그룹 대화가 생기면 이 질의도 함께 재설계된다.
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
                // 내가 보낸 것은 세지 않는다.
                message.senderId.ne(memberId),
                // 읽음 지점이 없으면 전부가 안 읽은 것이다.
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
    override fun findLastMessageId(conversationId: Long): Long? =
        queryFactory
            .select(message.id.max())
            .from(message)
            .where(message.conversationId.eq(conversationId))
            .fetchOne()

    /**
     * 대화별 마지막 메시지를 한 번에 읽는다.
     *
     * **대화별 최대 createdAt과 일치하는 행만 읽는다.** 대상 대화의 메시지를 전부 읽어 첫
     * 건만 취하면 6개월치가 쌓인 대화에서 읽는 양이 사실상 무한해지고, 버릴 행까지 전부
     * 복호화하게 된다 — 페이지가 50건으로 제한돼도 대화당 메시지 수는 제한되지 않는다.
     *
     * 같은 시각의 행이 둘 이상이면 그만큼만 더 오고, id가 큰 쪽을 취한다.
     * JPQL에 `ROW_NUMBER() OVER (PARTITION BY ...)`가 없어 상관 서브쿼리로 편다.
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
                message.createdAt.eq(
                    JPAExpressions
                        .select(latest.createdAt.max())
                        .from(latest)
                        .where(latest.conversationId.eq(message.conversationId)),
                ),
            )
            .fetch()
            .groupBy { it.conversationId }
            .mapValues { (_, messages) ->
                messages.maxBy { requireNotNull(it.id) }.toMessageView(memberId)
            }
    }

    private fun participantCondition(memberId: Long): BooleanExpression =
        conversation.memberLow.eq(memberId).or(conversation.memberHigh.eq(memberId))

    private fun keysetCondition(
        timePath: DateTimePath<Instant>,
        idPath: NumberPath<Long>,
        cursor: CommunityCursor,
    ): BooleanExpression =
        timePath.lt(cursor.createdAt)
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

    /**
     * 본문이 빈 메시지는 복호화를 건너뛴다 — 빈 값을 복호화하려 들면 base64 디코딩에서
     * 터진다. 사진만 보낸 메시지와, 피드 인용만 있던 과거 메시지가 여기 해당한다.
     */
    private fun MessageEntity.toMessageView(memberId: Long) = MessageView(
        id = requireNotNull(id) { "조회된 메시지에는 id가 있어야 한다." },
        body = if (bodyEncrypted.isEmpty()) "" else cipher.decrypt(bodyEncrypted),
        senderId = senderId,
        ownedByViewer = senderId == memberId,
        viewerId = memberId,
        createdAt = requireNotNull(createdAt) { "조회된 메시지에는 createdAt이 있어야 한다." },
        photoKey = photoObjectKey,
    )
}
