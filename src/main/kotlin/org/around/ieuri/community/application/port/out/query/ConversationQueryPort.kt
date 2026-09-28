package org.around.ieuri.community.application.port.out.query

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.community.application.query.ConversationView
import org.around.ieuri.community.application.query.MessageView

/**
 * 대화·메시지 읽기 포트(R8).
 *
 * **참여자 판정은 여기서 하지 않는다** — 조회에서 참여자가 아니면 최상위 errors[]의
 * FORBIDDEN이라 서비스가 판정하고 예외를 고른다.
 */
interface ConversationQueryPort {
    /** 내가 참여한 대화. 최근 활동순(updatedAt DESC). */
    fun findConversations(search: ConversationsSearch): CursorPage<ConversationView>

    /** 대화의 메시지. 최신순 — 채팅 화면은 위로 올릴수록 과거를 더 불러온다. */
    fun findMessages(search: ConversationMessagesSearch): CursorPage<MessageView>

    /** 대화 단건. 존재·참여자 판정에 쓴다. 없으면 null. */
    fun findConversation(conversationId: Long, memberId: Long): ConversationView?

    /**
     * 메시지 단건. 새 메시지 구독이 이벤트의 id로 **DB에서 다시 읽을 때** 쓴다 —
     * 본문을 브로커에 싣지 않으므로(POL-013) 값의 원본은 항상 DB다.
     *
     * [memberId]는 ownedByViewer 판정에 쓴다. 참여자 검사는 구독 시작 시점에 이미 끝났다.
     */

    /**
     * 구독 스트림이 이벤트의 id로 메시지를 다시 읽는다.
     *
     * **대화 id로도 범위를 좁힌다** — 이벤트는 브로커에서 오는데 Valkey는 신뢰 경계가 아니다.
     * 채널 이름만 믿으면, 그 채널에 발행할 수 있는 무엇이든 임의의 메시지 id를 실어 남의 대화를
     * 복호화해 내려보내게 만들 수 있다.
     */
    fun findMessage(conversationId: Long, messageId: Long, memberId: Long): MessageView?

    /**
     * 대화별 안 읽은 메시지 수. **여러 대화를 한 번에 센다** — 목록을 그리며 대화마다 물으면
     * 20건에 조회가 20번 붙는다(N+1).
     *
     * 결과에 없는 대화는 0건이다. 자기가 보낸 메시지는 세지 않는다.
     */
    fun countUnreadOf(conversationIds: List<Long>, memberId: Long): Map<Long, Int>

    /** 그 대화의 마지막 메시지 id. 메시지가 없으면 null이다. */
    fun findLastMessageId(conversationId: Long): Long?
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
