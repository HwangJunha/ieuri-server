package org.around.ieuri.community.domain.conversation

import org.around.ieuri.community.domain.photo.PhotoObjectKey
import java.time.Instant

/**
 * 1:1 대화. 메시지를 품는 애그리게이트의 루트이며, 메시지는 이 루트를 통해서만 만든다.
 * - 참여자는 `memberLow < memberHigh`로 정렬된 쌍이다. (A,B)와 (B,A)는 같은 대화다. 메시지 없는 빈 대화가 있을 수 있다.
 * - 루트는 대화 한 행만 담는다. 최근 활동 시각·읽음 지점은 저장소가 앞으로만 가는 원자 UPDATE로 쓴다.
 */
data class Conversation(
    val id: Long?,
    val memberLow: Long,
    val memberHigh: Long,
    /** 정렬 키(최근 활동순). 메시지가 없으면 생성 시각이다. */
    val lastMessageAt: Instant?,
    /** 각 참여자가 마지막으로 읽은 메시지 id. null은 "아직 아무것도 읽지 않았다"이다. */
    val lastReadMessageIdLow: Long?,
    val lastReadMessageIdHigh: Long?,
) {
    fun hasParticipant(memberId: Long): Boolean = memberId == memberLow || memberId == memberHigh

    /** 참여자가 아니면 NOT_CONVERSATION_PARTICIPANT. */
    fun requireParticipant(memberId: Long) {
        if (!hasParticipant(memberId)) throw NotConversationParticipantException()
    }

    fun counterpartOf(memberId: Long): Long {
        requireParticipant(memberId)
        return if (memberId == memberLow) memberHigh else memberLow
    }

    fun lastReadMessageIdOf(memberId: Long): Long? {
        requireParticipant(memberId)
        return if (memberId == memberLow) lastReadMessageIdLow else lastReadMessageIdHigh
    }

    /** [memberId]의 읽음 지점을 [messageId]까지 올린다. 더 작은 id로는 되돌아가지 않는다(멱등). */
    fun markRead(memberId: Long, messageId: Long): Conversation {
        val current = lastReadMessageIdOf(memberId)
        if (current != null && current >= messageId) return this
        return if (memberId == memberLow) copy(lastReadMessageIdLow = messageId) else copy(lastReadMessageIdHigh = messageId)
    }

    /**
     * 참여자가 보내는 새 메시지. 본문은 트리밍 후 판정하고, 상한을 넘으면 자르지 않고 거부한다.
     * - 본문·사진·인용이 모두 비면 거부한다. 사진 키는 소유·확정만 본다 — 업로드 여부는 애플리케이션이 확인한다.
     */
    fun send(senderId: Long, body: String, photoKey: PhotoObjectKey?, spotQaOrigin: SpotQaOrigin?): Message {
        requireParticipant(senderId)
        val trimmed = body.trim()
        require(trimmed.length <= Message.BODY_MAX_LENGTH) { "메시지는 ${Message.BODY_MAX_LENGTH}자를 넘을 수 없습니다." }
        require(trimmed.isNotEmpty() || photoKey != null || spotQaOrigin != null) { "메시지를 입력해 주세요." }
        require(photoKey == null || photoKey.ownedBy(senderId)) { "본인에게 발급된 사진 키가 아닙니다." }
        require(photoKey == null || photoKey.confirmed) { "업로드 확인을 마치지 않은 사진 키입니다." }
        return Message(
            id = null,
            conversationId = requireNotNull(id) { "적재된 대화에만 메시지를 보낸다." },
            senderId = senderId,
            body = trimmed,
            photoKey = photoKey,
            spotQaOrigin = spotQaOrigin,
            createdAt = null,
        )
    }

    companion object {
        /** 인자 순서와 무관하게 정렬된 쌍으로 만든다. 자기 자신과의 대화는 SELF_CONVERSATION. */
        fun start(memberId: Long, counterpartId: Long): Conversation {
            if (memberId == counterpartId) throw SelfConversationException()
            require(memberId > 0 && counterpartId > 0) { "회원 id는 양수여야 합니다." }
            return Conversation(
                id = null,
                memberLow = minOf(memberId, counterpartId),
                memberHigh = maxOf(memberId, counterpartId),
                lastMessageAt = null,
                lastReadMessageIdLow = null,
                lastReadMessageIdHigh = null,
            )
        }
    }
}
