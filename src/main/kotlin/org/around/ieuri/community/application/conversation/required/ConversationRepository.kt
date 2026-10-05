package org.around.ieuri.community.application.conversation.required

import org.around.ieuri.community.application.conversation.provided.dto.ExpiredMessages
import org.around.ieuri.community.domain.conversation.Conversation
import org.around.ieuri.community.domain.conversation.Message
import java.time.Instant

/** 대화 애그리게이트 저장소. 루트는 대화 한 행만 읽는다. 본문·인용 발췌의 암복호화는 어댑터가 한다. */
interface ConversationRepository {
    fun findById(conversationId: Long): Conversation?

    /** 같은 쌍의 대화가 있으면 그 id, 없으면 만들어 그 id. 경합에서도 행은 하나다. */
    fun insertIfAbsent(conversation: Conversation): Long

    /** 저장된 메시지(id·생성 시각 포함). 인용이 있으면 함께 저장한다. */
    fun insertMessage(message: Message): Message

    /** 지워지지 않은 메시지만. 없는 id는 결과에 없다. */
    fun findMessages(messageIds: List<Long>): List<Message>

    /** 지워지지 않은 마지막 메시지 id. 메시지가 없으면 null. */
    fun lastMessageId(conversationId: Long): Long?

    /** 최근 활동 시각을 [at]으로 올린다. 더 늦은 값이 이미 있으면 그대로 둔다. */
    fun advanceLastMessageAt(conversationId: Long, at: Instant)

    /** 두 읽음 지점을 저장한다. 각 열은 더 큰 값으로만 바뀐다 — 동시 요청이 지점을 되돌리지 않는다. */
    fun advanceReadPoints(conversation: Conversation)

    /** [memberId]가 받은 메시지 중 [afterMessageId] 뒤의 것(지워지지 않은 것) 수. null이면 전부. */
    fun countUnread(conversationId: Long, memberId: Long, afterMessageId: Long?): Int

    /** [cutoff] 이전에 만들어진 메시지 행을 최대 [limit]건 물리 삭제한다. */
    fun deleteMessagesCreatedBefore(cutoff: Instant, limit: Int): ExpiredMessages

    /** 탈퇴 정리: 회원이 참여한 대화 id. */
    fun findIdsByParticipant(memberId: Long): List<Long>

    /** 그 대화에서 [senderId]가 보낸 메시지 삭제 표시. 대화는 남는다. 보관 기간이 지나면 이 행도 물리 삭제된다. */
    fun markMessagesDeletedBySender(conversationId: Long, senderId: Long, deletedAt: Instant): Int
}
