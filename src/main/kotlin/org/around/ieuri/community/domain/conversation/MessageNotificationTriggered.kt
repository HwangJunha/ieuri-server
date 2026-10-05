package org.around.ieuri.community.domain.conversation

/** DM 새 메시지 도착 알림 트리거. alarm이 구독한다. 본문을 싣지 않고 id만 싣는다(POL-013). */
data class MessageNotificationTriggered(
    val conversationId: Long,
    val messageId: Long,
    /** 보낸 사람. 수신자와 같을 수 없다. */
    val senderId: Long,
    val recipientId: Long,
)
