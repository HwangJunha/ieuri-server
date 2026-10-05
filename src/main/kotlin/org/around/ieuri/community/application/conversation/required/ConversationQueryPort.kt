package org.around.ieuri.community.application.conversation.required

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.community.application.conversation.provided.dto.ConversationView
import org.around.ieuri.community.application.conversation.provided.dto.MessageView

/** 대화·메시지 읽기 포트(R8). 참여자 판정은 여기서 하지 않고 서비스가 한다. */
interface ConversationQueryPort {
    /** 내가 참여한 대화. 최근 활동순(updatedAt DESC). */
    fun findConversations(search: ConversationsSearch): CursorPage<ConversationView>

    /** 대화의 메시지. 최신순. */
    fun findMessages(search: ConversationMessagesSearch): CursorPage<MessageView>

    /** 대화 단건. 존재·참여자 판정에 쓴다. 없으면 null. */
    fun findConversation(conversationId: Long, memberId: Long): ConversationView?

    /**
     * 메시지 단건. 새 메시지 구독이 이벤트의 id로 DB에서 다시 읽을 때 쓴다(POL-013).
     * - 대화 id로도 범위를 좁힌다 — 브로커는 신뢰 경계가 아니므로 빼면 남의 대화가 새어 나간다.
     * - [memberId]는 ownedByViewer 판정용이다. 참여자 검사는 구독 시작 시점에 끝났다.
     */
    fun findMessage(conversationId: Long, messageId: Long, memberId: Long): MessageView?
}

data class ConversationsSearch(
    val memberId: Long,
    val first: Int,
    val after: String?,
)

data class ConversationMessagesSearch(
    val conversationId: Long,
    val memberId: Long,
    val first: Int,
    val after: String?,
)
